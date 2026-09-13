package com.myster.search;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.general.thread.AsyncContext;
import com.general.thread.Invoker;
import com.general.thread.PromiseFuture;
import com.myster.hash.FileHash;
import com.myster.net.MysterAddress;
import com.myster.net.client.MysterDatagram;
import com.myster.net.client.MysterProtocol;
import com.myster.tracker.MysterServer;
import com.myster.tracker.Tracker;
import com.myster.type.MysterType;

class TestMultiSourceHashSearch {
    private final Tracker tracker = mock(Tracker.class);
    private final MysterProtocol protocol = mock(MysterProtocol.class);
    private final MysterDatagram datagram = mock(MysterDatagram.class);
    private final MysterType type = new MysterType(new byte[16]);
    private final FileHash hash = mock(FileHash.class);
    private final HashSearchListener listener = mock(HashSearchListener.class);
    private final MultiSourceHashSearch crawler = new MultiSourceHashSearch(tracker, protocol);

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void emptySeedsRetryAndStop(boolean serverWithoutAddress) throws Exception {
        MysterServer server = mock(MysterServer.class);
        when(server.getBestAddress()).thenReturn(Optional.empty());
        when(tracker.getAll(type)).thenReturn(serverWithoutAddress ? List.of(server) : List.of());
        CountDownLatch retries = new CountDownLatch(2);
        when(tracker.getTop(type, 200)).thenAnswer(_ -> {
            assertTrue(invoker().isInvokerThread());
            retries.countDown();
            return new MysterServer[0];
        });
        crawler.setTimeBetweenCrawls(20);
        try {
            crawler.addHash(type, hash, listener);
            assertTrue(retries.await(5, TimeUnit.SECONDS), "empty crawl should retry");
        } finally {
            crawler.removeHash(type, hash, listener);
            drain();
        }
        assertNull(currentPromise());
        verifyNoInteractions(datagram, listener);
    }

    @Test
    void rapidCommandsCancelOldRequestsAndRejectLateResults() throws Exception {
        seed();
        List<PromiseFuture<String>> requests = new ArrayList<>();
        List<AsyncContext<String>> contexts = new ArrayList<>();
        when(datagram.getFileFromHash(any(), eq(type), any())).thenAnswer(_ -> {
            assertTrue(invoker().isInvokerThread());
            PromiseFuture<String> future = PromiseFuture.newPromiseFuture(contexts::add);
            requests.add(future);
            return future;
        });
        FileHash second = mock(FileHash.class);
        crawler.addHash(type, hash, listener);
        crawler.addHash(type, second, listener);
        crawler.removeHash(type, hash, listener);
        crawler.removeHash(type, second, listener);
        drain();
        assertEquals(3, requests.size());
        assertTrue(requests.stream().allMatch(PromiseFuture::isCancelled));
        contexts.forEach(context -> context.setResult("late result"));
        drain();
        verifyNoInteractions(listener);
        assertNull(currentPromise());
    }

    @Test
    void foreignInvokerResultsAreDeliveredOnCrawlerInvokerAndRetryIsCancellable() throws Exception {
        seed();
        Invoker foreign = Invoker.newVThreadInvoker();
        AtomicReference<AsyncContext<String>> context = new AtomicReference<>();
        PromiseFuture<String> result = PromiseFuture.newPromiseFuture(context::set).withInvoker(foreign);
        when(datagram.getFileFromHash(any(), eq(type), eq(hash))).thenReturn(result);
        CountDownLatch delivered = new CountDownLatch(1);
        AtomicReference<Thread> callbackThread = new AtomicReference<>();
        HashSearchListener callback = _ -> {
            callbackThread.set(Thread.currentThread());
            delivered.countDown();
        };
        try {
            crawler.addHash(type, hash, callback);
            drain();
            context.get().setResult("found");
            assertTrue(delivered.await(5, TimeUnit.SECONDS));
            AtomicReference<Thread> invokerThread = new AtomicReference<>();
            invoker().invoke(() -> invokerThread.set(Thread.currentThread()));
            drain();
            assertSame(invokerThread.get(), callbackThread.get());
            PromiseFuture<?> retry = currentPromise();
            assertNotNull(retry);
            assertFalse(retry.isDone());
            crawler.removeHash(type, hash, callback);
            drain();
            assertTrue(retry.isCancelled());
        } finally {
            crawler.removeHash(type, hash, callback);
            drain();
            foreign.shutdown();
        }
    }

    @Test
    void resolvedServerIsCrawledAfterSeedRequestsFinish() throws Exception {
        seed();
        java.util.concurrent.atomic.AtomicInteger topCalls = new java.util.concurrent.atomic.AtomicInteger();
        when(datagram.getTopServers(any(), eq(type))).thenAnswer(_ ->
                PromiseFuture.newPromiseFuture(topCalls.getAndIncrement() == 0
                        ? new String[] {"127.0.0.2"} : new String[0]));
        CountDownLatch searched = new CountDownLatch(2);
        when(datagram.getFileFromHash(any(), eq(type), eq(hash))).thenAnswer(_ -> {
            searched.countDown();
            return PromiseFuture.newPromiseFuture("");
        });
        try {
            crawler.addHash(type, hash, listener);
            assertTrue(searched.await(5, TimeUnit.SECONDS), "resolved address must also be searched");
        } finally {
            crawler.removeHash(type, hash, listener);
            drain();
        }
    }

    @Test
    void lastResultIsDeliveredWhenAggregationRunsBeforeResultDispatch() throws Exception {
        seed();
        AtomicReference<AsyncContext<String>> context = new AtomicReference<>();
        PromiseFuture<String> result = PromiseFuture.newPromiseFuture(context::set);
        when(datagram.getFileFromHash(any(), eq(type), eq(hash))).thenReturn(result);
        CountDownLatch delivered = new CountDownLatch(1);
        HashSearchListener callback = _ -> delivered.countDown();
        try {
            crawler.addHash(type, hash, callback);
            drain();
            // Registered after allCallResults: let the invoker process aggregation while
            // this producer has not yet queued the request's ordinary result listeners.
            result.addSynchronousCallback(_ -> assertDoesNotThrow(this::drain));
            context.get().setResult("found");
            assertTrue(delivered.await(5, TimeUnit.SECONDS), "last hash result must be delivered");
        } finally {
            crawler.removeHash(type, hash, callback);
            drain();
        }
    }

    private void seed() throws Exception {
        when(protocol.getDatagram()).thenReturn(datagram);
        MysterServer server = mock(MysterServer.class);
        when(server.getBestAddress()).thenReturn(Optional.of(MysterAddress.createMysterAddress("127.0.0.1")));
        when(tracker.getTop(type, 200)).thenReturn(new MysterServer[] {server});
        when(datagram.getTopServers(any(), eq(type)))
                .thenAnswer(_ -> PromiseFuture.newPromiseFuture(new String[0]));
    }

    private static Invoker invoker() throws Exception {
        var field = MultiSourceHashSearch.class.getDeclaredField("INVOKER");
        field.setAccessible(true);
        return (Invoker) field.get(null);
    }

    private void drain() throws Exception {
        for (int i = 0; i < 8; i++) {
            invoker().waitForThread();
        }
    }

    private PromiseFuture<?> currentPromise() throws Exception {
        AtomicReference<PromiseFuture<?>> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        invoker().invoke(() -> {
            try {
                var batches = MultiSourceHashSearch.class.getDeclaredField("typeHashtable");
                batches.setAccessible(true);
                Object batch = ((Map<?, ?>) batches.get(crawler)).get(type);
                var promise = batch.getClass().getDeclaredField("promise");
                promise.setAccessible(true);
                result.set((PromiseFuture<?>) promise.get(batch));
            } catch (ReflectiveOperationException e) {
                failure.set(e);
            }
        });
        drain();
        assertNull(failure.get());
        return result.get();
    }
}

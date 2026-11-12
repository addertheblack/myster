package com.myster.search;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.general.thread.AsyncContext;
import com.general.thread.Invoker;
import com.general.thread.PromiseFuture;
import com.myster.net.MysterAddress;
import com.myster.net.client.MysterDatagram;
import com.myster.net.client.MysterProtocol;
import com.myster.tracker.MysterAddressIdentity;
import com.myster.tracker.MysterServer;
import com.myster.tracker.Tracker;
import com.myster.tracker.ui.KnownServerSource;
import com.myster.type.MysterType;

class TestServerSearch {
    private final MysterType type = new MysterType(new byte[16]);
    private final Tracker tracker = mock(Tracker.class);
    private final KnownServerSource source = mock(KnownServerSource.class);
    private final MysterProtocol protocol = mock(MysterProtocol.class);
    private final MysterDatagram datagram = mock(MysterDatagram.class);
    private final List<ServerSearchResult> results = new ArrayList<>();
    private final List<Integer> batchSizes = new ArrayList<>();
    private MysterAddress first;

    @BeforeEach
    void setup() throws Exception {
        first = address(1);
        when(protocol.getDatagram()).thenReturn(datagram);
        when(datagram.getTopServers(any(), eq(type)))
                .thenAnswer(_ -> PromiseFuture.newPromiseFuture(new String[0]));
    }

    private MysterAddress address(int suffix) throws Exception {
        return MysterAddress.createMysterAddress("192.0.2." + suffix);
    }

    private MysterServer server(MysterAddress address, String name, String description) {
        MysterServer server = mock(MysterServer.class);
        when(server.getBestAddress()).thenReturn(Optional.of(address));
        when(server.getIdentity()).thenReturn(new MysterAddressIdentity(address));
        when(server.getServerName()).thenReturn(name);
        when(server.getServerDescription()).thenReturn(description);
        when(server.knowsAboutType(type)).thenReturn(true);
        when(tracker.getQuickServerStats(address)).thenReturn(server);
        return server;
    }

    private PromiseFuture<Void> search(String query, String... onramps) {
        try (var statics = mockStatic(Tracker.class)) {
            statics.when(Tracker::getOnRamps).thenReturn(onramps);
            return new ServerSearch(protocol, tracker, source).search(type, query, batch -> {
                assertTrue(SwingUtilities.isEventDispatchThread());
                batchSizes.add(batch.size());
                results.addAll(batch);
            }, _ -> {});
        }
    }

    private void seed(MysterServer server) {
        when(tracker.getTop(type, 50)).thenReturn(new MysterServer[] {server});
    }

    private void complete(PromiseFuture<Void> future) throws Exception {
        future.get(5, TimeUnit.SECONDS);
        SwingUtilities.invokeAndWait(() -> {});
        verify(source, never()).forEachServer(any());
        verify(tracker, never()).addIp(any());
        verify(datagram, never()).getSearch(any(), any(), any());
        verify(protocol, never()).getStream();
    }

    @ParameterizedTest
    @CsvSource({"needle,Needle House,Other,true", "needle,Other,NEEDLE archive,true",
            "  needle  ,Other,needle archive,true", "missing,Name,Description,false",
            "' ',Name,Description,true", "ab,ends a,b begins,false", "192.0.2,Name,Description,false"})
    void matchesEitherFieldAndTrimsQuery(String query, String name, String description, boolean match)
            throws Exception {
        seed(server(first, name, description));
        complete(search(query));
        assertEquals(match ? 1 : 0, results.size());
        verifyNoInteractions(source);
    }

    @Test
    void missingFieldsDownStatusAndZeroFilesAreCachedMetadata() throws Exception {
        MysterServer server = server(first, null, null);
        when(server.getPingTime()).thenReturn(MysterServer.DOWN);
        seed(server);
        complete(search(""));
        assertEquals(1, results.size());
        assertEquals("", results.getFirst().name());
        assertEquals(first.toString(), results.getFirst().displayName());
        assertEquals("", results.getFirst().description());
        assertEquals(0, results.getFirst().files());
        assertEquals(MysterServer.DOWN, results.getFirst().ping());
        verifyNoInteractions(source);
    }

    @Test
    void normalizationDoesNotDependOnTurkishLocale() throws Exception {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            seed(server(first, "INDIE", ""));
            complete(search("indie"));
            assertEquals(1, results.size());
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void neverEnumeratesPoolOrEmitsAnUnencounteredCachedServer() throws Exception {
        seed(server(first, "No match", ""));
        server(address(99), "needle", "");
        complete(search("needle"));
        assertTrue(results.isEmpty());
        verify(tracker, never()).getQuickServerStats(address(99));
    }

    @Test
    void crawlsThroughNonmatchAndLoopsToCachedPeerOutsideInitialTypeList() throws Exception {
        MysterAddress second = address(2);
        seed(server(first, "Other", ""));
        server(second, "needle", "");
        when(datagram.getTopServers(any(), eq(type))).thenAnswer(invocation -> {
            MysterAddress target = invocation.getArgument(0, com.myster.net.client.ParamBuilder.class)
                    .getAddress().orElseThrow();
            return PromiseFuture.newPromiseFuture(target.equals(first)
                    ? new String[] {second.toString(), first.toString(), second.toString()}
                    : new String[] {first.toString()});
        });
        complete(search("needle"));
        assertEquals(List.of(second), results.stream().map(ServerSearchResult::address).toList());
        verify(datagram, times(2)).getTopServers(any(), eq(type));
        verifyNoInteractions(source);
    }

    @Test
    void resolvesCacheMissAndUsesPoolCorrectedAddressDespiteTopTenFailure() throws Exception {
        MysterAddress corrected = address(2);
        MysterServer result = server(corrected, "needle", "");
        MysterServer hint = mock(MysterServer.class);
        when(hint.getBestAddress()).thenReturn(Optional.of(first));
        seed(hint);
        when(source.resolveServer(first)).thenReturn(PromiseFuture.newPromiseFuture(result));
        when(datagram.getTopServers(any(), eq(type)))
                .thenReturn(PromiseFuture.newPromiseFutureException(new IOException("offline")));
        complete(search("needle"));
        assertEquals(corrected, results.getFirst().address());
        verify(source, times(1)).resolveServer(first);
    }

    @Test
    void deduplicatesByIdentityButNotNameAndRejectsUnadvertisedType() throws Exception {
        MysterServer one = server(first, "same", "");
        MysterServer alias = server(address(2), "same", "");
        var identity = one.getIdentity();
        when(alias.getIdentity()).thenReturn(identity);
        MysterServer different = server(address(3), "same", "");
        MysterServer unknownType = server(address(4), "same", "");
        when(unknownType.knowsAboutType(type)).thenReturn(false);
        when(tracker.getTop(type, 50)).thenReturn(new MysterServer[] {one, alias, different, unknownType});
        complete(search("same"));
        assertEquals(2, results.size());
        assertEquals(2, results.stream().map(ServerSearchResult::identity).distinct().count());
    }

    @Test
    void fallbackUsesOnlySelectedTypeAndEmptyWorkCompletes() throws Exception {
        when(tracker.getAll(type)).thenReturn(List.of());
        complete(search(""));
        assertTrue(results.isEmpty());
        verifyNoInteractions(datagram, source);
        MysterServer untried = server(first, "", "");
        when(tracker.getAll(type)).thenReturn(List.of(untried));
        complete(search(""));
        assertEquals(1, results.size());
    }

    @Test
    void onrampBootstrapsEmptyTypeListAndFailedDnsAlsoExhausts() throws Exception {
        MysterServer one = server(first, "needle", "");
        complete(search("needle", first.toString()));
        assertEquals(1, results.size());
        results.clear();
        complete(search("needle", "bad host.invalid"));
        assertTrue(results.isEmpty());
        verify(one, times(1)).getServerName();
    }

    @Test
    void cancellationMakesLateStatsMootAndCancelsRequest() throws Exception {
        MysterServer hint = mock(MysterServer.class);
        when(hint.getBestAddress()).thenReturn(Optional.of(first));
        seed(hint);
        AtomicReference<AsyncContext<MysterServer>> context = new AtomicReference<>();
        PromiseFuture<MysterServer> request = PromiseFuture.newPromiseFuture(context::set);
        CountDownLatch resolving = new CountDownLatch(1);
        when(source.resolveServer(first)).thenAnswer(_ -> {
            resolving.countDown();
            return request;
        });
        PromiseFuture<Void> search = search("");
        assertTrue(resolving.await(5, TimeUnit.SECONDS));
        search.cancel();
        drain();
        assertTrue(request.isCancelled());
        context.get().setResult(server(first, "late", ""));
        drain();
        assertTrue(results.isEmpty());
    }

    @Test
    void batchesLargeSeedFallbackOffEdtAndDeliversLastBatchBeforeCompletion() throws Exception {
        List<MysterServer> servers = new ArrayList<>();
        for (int i = 1; i <= 180; i++) {
            servers.add(server(address(i), "name " + i, "description"));
        }
        when(tracker.getAll(type)).thenAnswer(_ -> {
            assertFalse(SwingUtilities.isEventDispatchThread());
            return servers;
        });
        complete(search(""));
        assertEquals(180, results.size());
        assertTrue(batchSizes.stream().allMatch(size -> size > 0 && size <= 64));
        assertTrue(batchSizes.size() < 180);
    }

    @Test
    void foreignInvokerStatsStillPublishBeforeCompletionOnSearchInvoker() throws Exception {
        MysterServer hint = mock(MysterServer.class);
        when(hint.getBestAddress()).thenReturn(Optional.of(first));
        seed(hint);
        Invoker foreign = Invoker.newVThreadInvoker();
        try {
            MysterServer result = server(first, "", "");
            when(source.resolveServer(first)).thenReturn(PromiseFuture.newPromiseFuture(result)
                    .withInvoker(foreign));
            when(tracker.getQuickServerStats(first)).thenReturn(null);
            complete(search(""));
            assertEquals(1, results.size());
        } finally {
            foreign.shutdown();
        }
    }

    @Test
    void nonmatchesAdvanceProgressWhileTopTenIsStillPending() throws Exception {
        seed(server(first, "Other", ""));
        PromiseFuture<String[]> pendingTop = PromiseFuture.newPromiseFuture(context -> {});
        when(datagram.getTopServers(any(), eq(type))).thenReturn(pendingTop);
        CountDownLatch progress = new CountDownLatch(1);
        PromiseFuture<Void> future;
        try (var statics = mockStatic(Tracker.class)) {
            statics.when(Tracker::getOnRamps).thenReturn(new String[0]);
            future = new ServerSearch(protocol, tracker, source).search(type, "needle", results::addAll,
                    message -> {
                        if (message.equals("Checked 1 servers...")) {
                            progress.countDown();
                        }
                    });
        }
        try {
            assertTrue(progress.await(5, TimeUnit.SECONDS));
            assertFalse(future.isDone());
            assertTrue(results.isEmpty());
        } finally {
            future.cancel();
            drain();
        }
        assertTrue(pendingTop.isCancelled());
    }

    @Test
    void failedStatsDoesNotStopNeighborDiscovery() throws Exception {
        MysterAddress second = address(2);
        MysterServer hint = mock(MysterServer.class);
        when(hint.getBestAddress()).thenReturn(Optional.of(first));
        seed(hint);
        when(source.resolveServer(first)).thenReturn(
                PromiseFuture.newPromiseFutureException(new IOException("not responding")));
        server(second, "needle", "");
        when(datagram.getTopServers(any(), eq(type))).thenAnswer(invocation ->
                PromiseFuture.newPromiseFuture(invocation.getArgument(0,
                        com.myster.net.client.ParamBuilder.class).getAddress().orElseThrow().equals(first)
                        ? new String[] {second.toString()} : new String[0]));
        complete(search("needle"));
        assertEquals(List.of(second), results.stream().map(ServerSearchResult::address).toList());
    }

    @Test
    void cancellationOnEdtSuppressesAlreadyQueuedBatchesEvenAfterCrawlCompletion() throws Exception {
        seed(server(first, "needle", ""));
        SwingUtilities.invokeAndWait(() -> {
            PromiseFuture<Void> future = search("needle");
            try {
                // Hold the EDT until the worker queues its final batch and completes the crawl.
                future.get(5, TimeUnit.SECONDS);
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
            future.cancel();
        });
        SwingUtilities.invokeAndWait(() -> {});
        assertTrue(results.isEmpty());
        assertTrue(batchSizes.isEmpty());
    }

    private void drain() throws Exception {
        var field = ServerSearch.class.getDeclaredField("INVOKER");
        field.setAccessible(true);
        for (int i = 0; i < 8; i++) {
            ((Invoker) field.get(null)).waitForThread();
        }
        SwingUtilities.invokeAndWait(() -> {});
    }
}

package com.myster.net.stream.client.msdownload;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.general.thread.AsyncContext;
import com.general.thread.PromiseFuture;
import com.myster.cid.ServerCid;
import com.myster.threedns.ThreeDnsLookupResult;
import com.myster.threedns.VerifiedThreeDnsPeer;

class TestDownloadSourceRecovery {
    private record Query(ServerCid cid, PromiseFuture<ThreeDnsLookupResult> future,
                         AsyncContext<ThreeDnsLookupResult> context) {}

    private static ServerCid cid(int n) {
        byte[] bytes = new byte[16];
        bytes[15] = (byte) n;
        return new ServerCid(bytes);
    }

    private static ThreeDnsLookupResult exact(ServerCid cid) {
        VerifiedThreeDnsPeer peer = mock(VerifiedThreeDnsPeer.class);
        when(peer.cid()).thenReturn(cid);
        ThreeDnsLookupResult result = mock(ThreeDnsLookupResult.class);
        when(result.exactPeer()).thenReturn(Optional.of(peer));
        return result;
    }

    private static Query pending(ServerCid cid) {
        AtomicReference<AsyncContext<ThreeDnsLookupResult>> context = new AtomicReference<>();
        PromiseFuture<ThreeDnsLookupResult> future = PromiseFuture.newPromiseFuture(context::set);
        return new Query(cid, future, context.get());
    }

    private static <T> T take(BlockingQueue<T> queue) throws Exception {
        T value = queue.poll(5, TimeUnit.SECONDS);
        assertNotNull(value, "Expected recovery worker operation");
        return value;
    }

    @Test void submitsEverySavedCidWithoutWaitingForTransfers() throws Exception {
        List<ServerCid> saved = IntStream.range(0, 80).mapToObj(TestDownloadSourceRecovery::cid).toList();
        BlockingQueue<ServerCid> submitted = new LinkedBlockingQueue<>();
        DownloadSourceRecovery recovery = new DownloadSourceRecovery(() -> saved,
                cid -> PromiseFuture.newPromiseFuture(exact(cid)), peer -> submitted.add(peer.cid()));
        recovery.start();
        assertTrue(recovery.awaitTermination(5, TimeUnit.SECONDS));
        recovery.cancel();
        assertEquals(Set.copyOf(saved), Set.copyOf(submitted));
    }

    @Test void startsEveryLookupBeforeAnyLookupCompletes() throws Exception {
        BlockingQueue<Query> queries = new LinkedBlockingQueue<>();
        BlockingQueue<ServerCid> candidates = new LinkedBlockingQueue<>();
        DownloadSourceRecovery recovery = new DownloadSourceRecovery(
                () -> List.of(cid(1), cid(2), cid(3)), cid -> {
                    Query query = pending(cid);
                    queries.add(query);
                    return query.future();
                }, peer -> candidates.add(peer.cid()));
        recovery.start();
        try {
            Query first = take(queries);
            Query second = take(queries);
            Query third = take(queries);
            assertEquals(Set.of(cid(1), cid(2), cid(3)), Set.of(first.cid(), second.cid(), third.cid()));
            for (Query query : List.of(first, second, third)) {
                query.context().setResult(exact(query.cid()));
            }
            assertEquals(Set.of(cid(1), cid(2), cid(3)),
                    Set.of(take(candidates), take(candidates), take(candidates)));
            assertTrue(recovery.awaitTermination(5, TimeUnit.SECONDS));
        } finally {
            recovery.cancel();
        }
    }

    @Test void nonExactOrWrongCidNeverStartsPreparation() throws Exception {
        AtomicBoolean prepared = new AtomicBoolean();
        ThreeDnsLookupResult absent = mock(ThreeDnsLookupResult.class);
        when(absent.exactPeer()).thenReturn(Optional.empty());
        DownloadSourceRecovery recovery = new DownloadSourceRecovery(() -> List.of(cid(1), cid(2)),
                target -> PromiseFuture.newPromiseFuture(target.equals(cid(1)) ? absent : exact(cid(99))),
                peer -> prepared.set(true));
        recovery.start();
        assertTrue(recovery.awaitTermination(5, TimeUnit.SECONDS));
        assertFalse(prepared.get());
    }

    @Test void deadlineCancelsStalledLookup() throws Exception {
        Query query = pending(cid(1));
        AtomicBoolean submitted = new AtomicBoolean();
        DownloadSourceRecovery recovery = new DownloadSourceRecovery(() -> List.of(cid(1)),
                target -> query.future(), peer -> submitted.set(true), Duration.ofMillis(100));
        recovery.start();
        assertTrue(recovery.awaitTermination(5, TimeUnit.SECONDS));
        assertTrue(query.future().isCancelled());
        assertFalse(submitted.get());
    }

    @Test void cancelDuringLookupIgnoresLateResults() throws Exception {
        BlockingQueue<Query> queries = new LinkedBlockingQueue<>();
        AtomicBoolean prepared = new AtomicBoolean();
        DownloadSourceRecovery recovery = new DownloadSourceRecovery(() -> List.of(cid(1)), cid -> {
            Query query = pending(cid);
            queries.add(query);
            return query.future();
        }, peer -> prepared.set(true));
        recovery.start();
        Query query = take(queries);
        recovery.cancel();
        assertTrue(recovery.awaitTermination(5, TimeUnit.SECONDS));
        query.context().setResult(exact(query.cid()));
        assertTrue(query.future().isCancelled());
        assertFalse(prepared.get());
    }

    @Test void emptyListAndCancellationBeforeStartNeedNoNetworking() throws Exception {
        AtomicBoolean queried = new AtomicBoolean();
        DownloadSourceRecovery empty = new DownloadSourceRecovery(List::of, cid -> {
            queried.set(true);
            return null;
        }, peer -> queried.set(true));
        empty.start();
        assertTrue(empty.awaitTermination(5, TimeUnit.SECONDS));
        DownloadSourceRecovery cancelled = new DownloadSourceRecovery(() -> List.of(cid(1)), cid -> {
            queried.set(true);
            return null;
        }, peer -> queried.set(true));
        cancelled.cancel();
        cancelled.start();
        assertTrue(cancelled.awaitTermination(5, TimeUnit.SECONDS));
        assertFalse(queried.get());
    }

    @Test void expectedLookupFailuresDoNotEscapeOrSubmitCandidates() throws Exception {
        BlockingQueue<Throwable> failures = new LinkedBlockingQueue<>();
        AtomicBoolean submitted = new AtomicBoolean();
        for (Exception failure : List.of(new java.io.IOException("offline"),
                new java.util.concurrent.TimeoutException("lookup timeout"),
                new java.util.concurrent.CancellationException("lookup cancelled"))) {
            DownloadSourceRecovery recovery = new DownloadSourceRecovery(() -> List.of(cid(1)), target -> {
                Thread.currentThread().setUncaughtExceptionHandler((_, error) -> failures.add(error));
                return PromiseFuture.newPromiseFutureException(failure);
            }, peer -> submitted.set(true));
            recovery.start();
            assertTrue(recovery.awaitTermination(5, TimeUnit.SECONDS));
        }
        assertFalse(submitted.get());
        assertTrue(failures.isEmpty());
    }

    @Test void wrappedProgrammingFailuresReachTheWorkerExceptionHandler() throws Exception {
        for (Throwable failure : List.of(new IllegalStateException("bug"), new AssertionError("invariant"),
                new Exception("unexpected checked failure"))) {
            BlockingQueue<Throwable> failures = new LinkedBlockingQueue<>();
            AtomicBoolean submitted = new AtomicBoolean();
            DownloadSourceRecovery recovery = new DownloadSourceRecovery(() -> List.of(cid(1)), target -> {
                Thread.currentThread().setUncaughtExceptionHandler((_, error) -> failures.add(error));
                return PromiseFuture.newPromiseFuture(context -> context.setException(failure));
            }, peer -> submitted.set(true));
            recovery.start();
            Throwable escaped = take(failures);
            assertTrue(recovery.awaitTermination(5, TimeUnit.SECONDS));
            if (failure instanceof RuntimeException || failure instanceof Error) {
                assertSame(failure, escaped);
            } else {
                assertInstanceOf(IllegalStateException.class, escaped);
                assertSame(failure, escaped.getCause());
            }
            assertFalse(submitted.get());
        }
    }
}

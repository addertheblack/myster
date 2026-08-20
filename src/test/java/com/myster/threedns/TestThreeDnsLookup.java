package com.myster.threedns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.security.PublicKey;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import com.general.thread.AsyncContext;
import com.general.thread.Cancellable;
import com.general.thread.Invoker;
import com.general.thread.PromiseFuture;
import com.myster.cid.ServerCid;
import com.myster.net.MysterAddress;
import com.myster.tracker.PublicKeyIdentity;

class TestThreeDnsLookup {
    private static final Invoker TEST_INVOKER = Invoker.newVThreadInvoker();

    @AfterAll
    static void shutDownInvoker() {
        TEST_INVOKER.shutdown();
    }

    @Test
    void autoSeededMultiHopLookupVerifiesEveryHintIncludingRightGroup() throws Exception {
        ThreeDnsAddressCandidate targetCandidate = candidate(1, 1, 7001);
        ServerCid target = targetCandidate.cid();
        List<ThreeDnsAddressCandidate> intermediates = List.of(
                candidate(2, 2, 7002), candidate(3, 3, 7003)).stream()
                .sorted((a, b) -> target.comparePredecessorDistance(a.cid(), b.cid()))
                .toList();
        ThreeDnsAddressCandidate closer = intermediates.getFirst();
        ThreeDnsAddressCandidate seed = intermediates.getLast();
        AtomicInteger seedCalls = new AtomicInteger();
        List<ThreeDnsAddressCandidate> queried = new ArrayList<>();
        ManualDeadline deadline = new ManualDeadline();
        ThreeDnsLookup lookup = new ThreeDnsLookup(
                (requestedTarget, perSideLimit) -> {
                    seedCalls.incrementAndGet();
                    assertEquals(target, requestedTarget);
                    assertEquals(2, perSideLimit);
                    return set(seed);
                },
                (candidate, requestedTarget, _) -> {
                    queried.add(candidate);
                    if (candidate.equals(seed)) {
                        return completed(candidate, new ThreeDnsAddressCandidateSet(
                                Optional.empty(), List.of(), List.of(closer)));
                    }
                    if (candidate.equals(closer)) {
                        return completed(candidate, new ThreeDnsAddressCandidateSet(
                                Optional.of(targetCandidate), List.of(), List.of()));
                    }
                    return completed(candidate, ThreeDnsAddressCandidateSet.empty());
                },
                limits(32, 2),
                deadline,
                TEST_INVOKER);

        ThreeDnsLookupResult result = lookup.resolve(target).get();
        TEST_INVOKER.waitForThread();

        assertEquals(1, seedCalls.get());
        assertEquals(List.of(seed, closer, targetCandidate), queried);
        assertEquals(ThreeDnsLookupResult.Status.EXACT_VERIFIED, result.status());
        assertEquals(targetCandidate.address(), result.exactAddress().orElseThrow());
        assertEquals(result.exactPeer(), result.closestPeer());
        assertTrue(deadline.cancelled);
    }

    @Test
    void exactHintDoesNotCompleteUntilCandidateAnswers() throws Exception {
        ThreeDnsAddressCandidate exact = candidate(10, 10, 7010);
        AtomicReference<AsyncContext<ThreeDnsVerifiedQueryResult>> queryContext =
                new AtomicReference<>();
        PromiseFuture<ThreeDnsVerifiedQueryResult> pending =
                PromiseFuture.newPromiseFuture(queryContext::set);
        ThreeDnsLookup lookup = lookup(set(exact), (_, _, _) -> pending, limits(32, 2),
                                      new ManualDeadline());

        PromiseFuture<ThreeDnsLookupResult> result = lookup.resolve(exact.cid());

        TEST_INVOKER.waitForThread();
        assertFalse(result.isDone());
        queryContext.get().setResult(verified(exact, ThreeDnsAddressCandidateSet.empty()));
        assertEquals(ThreeDnsLookupResult.Status.EXACT_VERIFIED, result.get().status());
    }

    @Test
    void emptySnapshotReturnsNoRouteWithoutQuery() throws Exception {
        AtomicInteger queries = new AtomicInteger();
        ThreeDnsLookup lookup = lookup(
                ThreeDnsAddressCandidateSet.empty(),
                (_, _, _) -> {
                    queries.incrementAndGet();
                    throw new AssertionError("No query expected");
                },
                limits(32, 2),
                new ManualDeadline());

        ThreeDnsLookupResult result = lookup.resolve(cid(40)).get();

        assertEquals(ThreeDnsLookupResult.Status.NO_ROUTE, result.status());
        assertTrue(result.closestPeer().isEmpty());
        assertEquals(0, queries.get());
    }

    @Test
    void failedCandidateFallsBackAndClosestExhaustionIsExplicit() throws Exception {
        ThreeDnsAddressCandidate failed = candidate(20, 20, 7020);
        ThreeDnsAddressCandidate successful = candidate(21, 21, 7021);
        ThreeDnsLookup lookup = lookup(
                new ThreeDnsAddressCandidateSet(Optional.empty(),
                                                List.of(failed, successful),
                                                List.of()),
                (candidate, _, _) -> candidate.equals(failed)
                        ? PromiseFuture.newPromiseFutureException(new java.io.IOException("down"))
                        : completed(candidate, ThreeDnsAddressCandidateSet.empty()),
                limits(32, 1),
                new ManualDeadline());

        ThreeDnsLookupResult result = lookup.resolve(cid(41)).get();

        assertEquals(ThreeDnsLookupResult.Status.CLOSEST_VERIFIED, result.status());
        assertEquals(successful.identity(), result.closestPeer().orElseThrow().identity());
        assertTrue(result.exactPeer().isEmpty());
    }

    @Test
    void queryLimitRetainsClosestVerifiedPeer() throws Exception {
        ServerCid target = cid(42);
        List<ThreeDnsAddressCandidate> ordered = List.of(
                candidate(30, 30, 7030), candidate(31, 31, 7031)).stream()
                .sorted((a, b) -> target.comparePredecessorDistance(a.cid(), b.cid()))
                .toList();
        ThreeDnsAddressCandidate closer = ordered.getFirst();
        ThreeDnsAddressCandidate seed = ordered.getLast();
        ThreeDnsLookup lookup = lookup(
                set(seed),
                (candidate, _, _) -> completed(candidate, set(closer)),
                limits(1, 1),
                new ManualDeadline());

        ThreeDnsLookupResult result = lookup.resolve(target).get();

        assertEquals(ThreeDnsLookupResult.Status.QUERY_LIMIT_REACHED, result.status());
        assertEquals(seed.identity(), result.closestPeer().orElseThrow().identity());
    }

    @Test
    void deadlineCancelsOutstandingQueryAndPreservesNoUnverifiedPeer() throws Exception {
        ThreeDnsAddressCandidate seed = candidate(40, 40, 7040);
        PromiseFuture<ThreeDnsVerifiedQueryResult> pending = PromiseFuture.newPromiseFuture(_ -> {});
        ManualDeadline deadline = new ManualDeadline();
        ThreeDnsLookup lookup = lookup(set(seed), (_, _, _) -> pending, limits(32, 2), deadline);
        PromiseFuture<ThreeDnsLookupResult> future = lookup.resolve(cid(43));

        TEST_INVOKER.waitForThread();
        deadline.fire();
        ThreeDnsLookupResult result = future.get();

        assertEquals(ThreeDnsLookupResult.Status.DEADLINE_REACHED, result.status());
        assertTrue(result.closestPeer().isEmpty());
        assertTrue(pending.isCancelled());
    }

    @Test
    void callerCancellationCancelsDeadlineAndAllQueries() throws Exception {
        ThreeDnsAddressCandidate first = candidate(50, 50, 7050);
        ThreeDnsAddressCandidate second = candidate(51, 51, 7051);
        List<PromiseFuture<ThreeDnsVerifiedQueryResult>> pending = new ArrayList<>();
        ManualDeadline deadline = new ManualDeadline();
        ThreeDnsLookup lookup = lookup(
                new ThreeDnsAddressCandidateSet(Optional.empty(), List.of(first, second), List.of()),
                (_, _, _) -> {
                    PromiseFuture<ThreeDnsVerifiedQueryResult> query =
                            PromiseFuture.newPromiseFuture(_ -> {});
                    pending.add(query);
                    return query;
                },
                limits(32, 2),
                deadline);
        PromiseFuture<ThreeDnsLookupResult> future = lookup.resolve(cid(44));

        TEST_INVOKER.waitForThread();
        assertEquals(2, pending.size());
        assertTrue(future.cancel(true));
        TEST_INVOKER.waitForThread();
        TEST_INVOKER.waitForThread();

        assertTrue(deadline.cancelled);
        assertTrue(pending.stream().allMatch(PromiseFuture::isCancelled));
        assertThrows(CancellationException.class, future::get);
    }

    @Test
    void concurrencyLimitReplenishesOneSlotAfterFailure() throws Exception {
        List<ThreeDnsAddressCandidate> seeds = List.of(
                candidate(55, 55, 7055),
                candidate(56, 56, 7056),
                candidate(57, 57, 7057));
        List<AsyncContext<ThreeDnsVerifiedQueryResult>> contexts = new ArrayList<>();
        List<PromiseFuture<ThreeDnsVerifiedQueryResult>> queries = new ArrayList<>();
        ThreeDnsLookup lookup = lookup(
                new ThreeDnsAddressCandidateSet(Optional.empty(), seeds, List.of()),
                (_, _, _) -> {
                    AtomicReference<AsyncContext<ThreeDnsVerifiedQueryResult>> reference =
                            new AtomicReference<>();
                    PromiseFuture<ThreeDnsVerifiedQueryResult> query =
                            PromiseFuture.newPromiseFuture(reference::set);
                    contexts.add(reference.get());
                    queries.add(query);
                    return query;
                },
                limits(32, 2),
                new ManualDeadline());
        PromiseFuture<ThreeDnsLookupResult> future = lookup.resolve(cid(46));

        TEST_INVOKER.waitForThread();
        assertEquals(2, queries.size());
        contexts.getFirst().setException(new java.io.IOException("down"));
        TEST_INVOKER.waitForThread();
        assertEquals(3, queries.size());
        assertEquals(2, queries.stream().filter(query -> !query.isDone()).count());

        future.cancel(true);
    }

    @Test
    void exactCompletionCancelsOtherInFlightQueryAndRejectsLateChange() throws Exception {
        ThreeDnsAddressCandidate exact = candidate(60, 60, 7060);
        ThreeDnsAddressCandidate other = candidate(61, 61, 7061);
        Map<ThreeDnsAddressCandidate, AsyncContext<ThreeDnsVerifiedQueryResult>> contexts =
                new LinkedHashMap<>();
        Map<ThreeDnsAddressCandidate, PromiseFuture<ThreeDnsVerifiedQueryResult>> queries =
                new LinkedHashMap<>();
        ThreeDnsLookup lookup = lookup(
                new ThreeDnsAddressCandidateSet(Optional.of(exact), List.of(other), List.of()),
                (candidate, _, _) -> {
                    AtomicReference<AsyncContext<ThreeDnsVerifiedQueryResult>> reference =
                            new AtomicReference<>();
                    PromiseFuture<ThreeDnsVerifiedQueryResult> query =
                            PromiseFuture.newPromiseFuture(reference::set);
                    contexts.put(candidate, reference.get());
                    queries.put(candidate, query);
                    return query;
                },
                limits(32, 2),
                new ManualDeadline());
        PromiseFuture<ThreeDnsLookupResult> future = lookup.resolve(exact.cid());

        TEST_INVOKER.waitForThread();
        assertEquals(2, queries.size());
        contexts.get(exact).setResult(verified(exact, ThreeDnsAddressCandidateSet.empty()));
        ThreeDnsLookupResult result = future.get();
        TEST_INVOKER.waitForThread();

        assertEquals(ThreeDnsLookupResult.Status.EXACT_VERIFIED, result.status());
        assertTrue(queries.get(other).isCancelled());
        assertFalse(contexts.get(other).setResult(verified(other, ThreeDnsAddressCandidateSet.empty())));
        assertEquals(ThreeDnsLookupResult.Status.EXACT_VERIFIED, future.get().status());
    }

    @Test
    void failedAddressCanUseOneAlternateForSameIdentity() throws Exception {
        PublicKeyIdentity identity = identity(70);
        ThreeDnsAddressCandidate first = candidate(identity, 70, 7070);
        ThreeDnsAddressCandidate alternate = candidate(identity, 71, 7071);
        AtomicInteger attempts = new AtomicInteger();
        ThreeDnsLookup lookup = lookup(
                new ThreeDnsAddressCandidateSet(Optional.empty(),
                                                List.of(first),
                                                List.of(alternate)),
                (candidate, _, _) -> attempts.getAndIncrement() == 0
                        ? PromiseFuture.newPromiseFutureException(new java.io.IOException("stale"))
                        : completed(candidate, ThreeDnsAddressCandidateSet.empty()),
                limits(32, 2),
                new ManualDeadline());

        ThreeDnsLookupResult result = lookup.resolve(cid(45)).get();

        assertEquals(2, attempts.get());
        assertEquals(ThreeDnsLookupResult.Status.CLOSEST_VERIFIED, result.status());
        assertEquals(identity, result.closestPeer().orElseThrow().identity());
    }

    @Test
    void limitsRejectInvalidResourcePolicy() {
        assertThrows(IllegalArgumentException.class,
                     () -> new ThreeDnsLookup.Limits(0, 64, 32, 2, 2, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class,
                     () -> new ThreeDnsLookup.Limits(2, 64, 32, 2, 2, Duration.ZERO));
    }

    private static ThreeDnsLookup lookup(ThreeDnsAddressCandidateSet seeds,
                                         ThreeDnsLookup.PeerQuery query,
                                         ThreeDnsLookup.Limits limits,
                                         ManualDeadline deadline) {
        return new ThreeDnsLookup((_, _) -> seeds, query, limits, deadline, TEST_INVOKER);
    }

    private static ThreeDnsLookup.Limits limits(int maxQueries, int maxInFlight) {
        return new ThreeDnsLookup.Limits(2, 64, maxQueries, maxInFlight, 2, Duration.ofSeconds(60));
    }

    private static PromiseFuture<ThreeDnsVerifiedQueryResult> completed(
            ThreeDnsAddressCandidate responder,
            ThreeDnsAddressCandidateSet hints) {
        return PromiseFuture.newPromiseFuture(verified(responder, hints));
    }

    private static ThreeDnsVerifiedQueryResult verified(ThreeDnsAddressCandidate responder,
                                                        ThreeDnsAddressCandidateSet hints) {
        return new ThreeDnsVerifiedQueryResult(new VerifiedThreeDnsPeer(responder), hints);
    }

    private static ThreeDnsAddressCandidateSet set(ThreeDnsAddressCandidate candidate) {
        return new ThreeDnsAddressCandidateSet(Optional.empty(), List.of(candidate), List.of());
    }

    private static ThreeDnsAddressCandidate candidate(int key,
                                                       int lastOctet,
                                                       int port) throws Exception {
        return candidate(identity(key), lastOctet, port);
    }

    private static ThreeDnsAddressCandidate candidate(PublicKeyIdentity identity,
                                                       int lastOctet,
                                                       int port) throws Exception {
        return new ThreeDnsAddressCandidate(
                identity,
                new MysterAddress(InetAddress.getByName("127.0.0." + lastOctet), port));
    }

    private static PublicKeyIdentity identity(int value) {
        return new PublicKeyIdentity(new TestPublicKey(new byte[] {
                (byte) (value >>> 8), (byte) value }));
    }

    private static ServerCid cid(int value) {
        byte[] bytes = new byte[ServerCid.LENGTH];
        bytes[bytes.length - 1] = (byte) value;
        return new ServerCid(bytes);
    }

    private static final class ManualDeadline implements ThreeDnsLookup.DeadlineScheduler {
        private Runnable task;
        private boolean cancelled;

        @Override
        public Cancellable schedule(Runnable task, Duration delay) {
            this.task = task;
            return () -> cancelled = true;
        }

        private void fire() {
            task.run();
        }
    }

    private static final class TestPublicKey implements PublicKey {
        private final byte[] encoded;

        private TestPublicKey(byte[] encoded) {
            this.encoded = encoded.clone();
        }

        @Override
        public String getAlgorithm() {
            return "test";
        }

        @Override
        public String getFormat() {
            return "test";
        }

        @Override
        public byte[] getEncoded() {
            return encoded.clone();
        }

        @Override
        public boolean equals(Object object) {
            return object instanceof TestPublicKey other && Arrays.equals(encoded, other.encoded);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(encoded);
        }
    }
}

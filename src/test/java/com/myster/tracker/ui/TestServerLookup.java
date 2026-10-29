package com.myster.tracker.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.general.thread.AsyncContext;
import com.general.thread.Invoker;
import com.general.thread.PromiseFuture;
import com.myster.cid.ServerCid;
import com.myster.net.MysterAddress;
import com.myster.tracker.MysterServer;

class TestServerLookup {
    private static final ServerLookupCandidate CANDIDATE =
            ServerLookupCandidate.parse("example.com").orElseThrow();

    @Test
    void reportsStagesInOrderAndCompletesWithResolvedServer() throws Exception {
        AtomicReference<AsyncContext<Void>> delayContext = new AtomicReference<>();
        AtomicReference<AsyncContext<MysterServer>> statsContext = new AtomicReference<>();
        AtomicReference<MysterAddress> requestedAddress = new AtomicReference<>();
        CountDownLatch statsRequested = new CountDownLatch(1);
        MysterAddress address = loopback();
        MysterServer server = Mockito.mock(MysterServer.class);
        List<ServerLookup.Stage> stages =
                Collections.synchronizedList(new ArrayList<>());
        KnownServerSource source = source(requestedAddress, statsRequested,
                PromiseFuture.newPromiseFuture(statsContext::set));
        ServerLookup lookup = lookup(source,
                duration -> PromiseFuture.newPromiseFuture(delayContext::set),
                _ -> address);

        PromiseFuture<MysterServer> result = lookup.start(CANDIDATE,
                update -> stages.add(update.stage()));
        delayContext.get().setResult(null);
        assertTrue(statsRequested.await(2, TimeUnit.SECONDS));
        statsContext.get().setResult(server);

        assertSame(server, result.get());
        assertEquals(address, requestedAddress.get());
        assertEquals(List.of(ServerLookup.Stage.WAITING,
                             ServerLookup.Stage.RESOLVING,
                             ServerLookup.Stage.CONTACTING), stages);
    }

    @Test
    void cancellationDuringDelayStartsNeitherDnsNorStats() {
        AtomicReference<PromiseFuture<Void>> delay = new AtomicReference<>();
        AtomicBoolean dnsCalled = new AtomicBoolean();
        AtomicBoolean statsCalled = new AtomicBoolean();
        KnownServerSource source = source(_ -> statsCalled.set(true),
                PromiseFuture.newPromiseFuture(_ -> {}));
        ServerLookup lookup = lookup(source, _ -> {
            PromiseFuture<Void> future = PromiseFuture.newPromiseFuture(_ -> {});
            delay.set(future);
            return future;
        }, _ -> {
            dnsCalled.set(true);
            return loopback();
        });

        PromiseFuture<MysterServer> result = lookup.start(CANDIDATE, _ -> {});
        result.cancel();

        assertTrue(result.isCancelled());
        assertTrue(delay.get().isCancelled());
        assertFalse(dnsCalled.get());
        assertFalse(statsCalled.get());
    }

    @Test
    void cancellationDuringDnsPreventsStats() throws Exception {
        CountDownLatch dnsEntered = new CountDownLatch(1);
        CountDownLatch releaseDns = new CountDownLatch(1);
        CountDownLatch statsRequested = new CountDownLatch(1);
        AtomicReference<AsyncContext<Void>> delayContext = new AtomicReference<>();
        KnownServerSource source = source(_ -> statsRequested.countDown(),
                PromiseFuture.newPromiseFuture(_ -> {}));
        ServerLookup lookup = lookup(source,
                _ -> PromiseFuture.newPromiseFuture(delayContext::set),
                _ -> {
                    dnsEntered.countDown();
                    await(releaseDns);
                    return loopback();
                });

        PromiseFuture<MysterServer> result = lookup.start(CANDIDATE, _ -> {});
        delayContext.get().setResult(null);
        assertTrue(dnsEntered.await(2, TimeUnit.SECONDS));
        result.cancel();
        releaseDns.countDown();

        assertFalse(statsRequested.await(100, TimeUnit.MILLISECONDS));
        assertTrue(result.isCancelled());
    }

    @Test
    void cancellationDuringStatsCancelsStatsFuture() throws Exception {
        AtomicReference<AsyncContext<Void>> delayContext = new AtomicReference<>();
        PromiseFuture<MysterServer> stats = PromiseFuture.newPromiseFuture(_ -> {});
        CountDownLatch statsRequested = new CountDownLatch(1);
        KnownServerSource source = source(_ -> statsRequested.countDown(), stats);
        ServerLookup lookup = lookup(source,
                _ -> PromiseFuture.newPromiseFuture(delayContext::set),
                _ -> loopback());

        PromiseFuture<MysterServer> result = lookup.start(CANDIDATE, _ -> {});
        delayContext.get().setResult(null);
        assertTrue(statsRequested.await(2, TimeUnit.SECONDS));
        result.cancel();

        assertTrue(stats.isCancelled());
        assertTrue(result.isCancelled());
    }

    @Test
    void dnsAndStatsFailuresRemainDistinct() throws Exception {
        AtomicReference<AsyncContext<Void>> firstDelay = new AtomicReference<>();
        UnknownHostException dnsFailure = new UnknownHostException("missing");
        ServerLookup dnsLookup = lookup(source(_ -> {}, PromiseFuture.newPromiseFuture(_ -> {})),
                _ -> PromiseFuture.newPromiseFuture(firstDelay::set),
                _ -> { throw dnsFailure; });
        PromiseFuture<MysterServer> dnsResult = dnsLookup.start(CANDIDATE, _ -> {});
        firstDelay.get().setResult(null);

        ExecutionException dnsThrown = assertThrows(ExecutionException.class, dnsResult::get);
        assertSame(dnsFailure, dnsThrown.getCause());

        AtomicReference<AsyncContext<Void>> secondDelay = new AtomicReference<>();
        IOException statsFailure = new IOException("no response");
        ServerLookup statsLookup = lookup(
                source(_ -> {}, PromiseFuture.newPromiseFutureException(statsFailure)),
                _ -> PromiseFuture.newPromiseFuture(secondDelay::set),
                _ -> loopback());
        PromiseFuture<MysterServer> statsResult = statsLookup.start(CANDIDATE, _ -> {});
        secondDelay.get().setResult(null);

        ExecutionException statsThrown = assertThrows(ExecutionException.class, statsResult::get);
        assertSame(statsFailure, statsThrown.getCause());
    }

    @Test
    void cidLookupWaitsForTheDelayAndBypassesHostnameDns() throws Exception {
        var candidate = (ServerLookupCandidate.Cid) ServerLookupCandidate.parse(
                "46e22ea215589a03f3d8751d241405fb").orElseThrow();
        AtomicReference<AsyncContext<Void>> delayContext = new AtomicReference<>();
        KnownServerSource source = Mockito.mock(KnownServerSource.class);
        MysterServer server = Mockito.mock(MysterServer.class);
        Mockito.when(source.resolveServerCid(candidate.cid())).thenReturn(PromiseFuture.newPromiseFuture(server));
        List<ServerLookup.Stage> stages = new ArrayList<>();
        ServerLookup lookup = lookup(source,
                _ -> PromiseFuture.newPromiseFuture(delayContext::set),
                _ -> { throw new AssertionError("CID input must use 3DNS instead of hostname DNS"); });

        PromiseFuture<MysterServer> result = lookup.start(candidate,
                update -> stages.add(update.stage()));
        Mockito.verifyNoInteractions(source);
        delayContext.get().setResult(null);

        assertSame(server, result.get(2, TimeUnit.SECONDS));
        assertEquals(List.of(ServerLookup.Stage.WAITING, ServerLookup.Stage.RESOLVING), stages);
        Mockito.verify(source).resolveServerCid(candidate.cid());
        Mockito.verifyNoMoreInteractions(source);
    }

    @Test
    void cancellingCidInputDuringDelayNeverStartsLookup() {
        var candidate = ServerLookupCandidate.parse("46e22ea215589a03f3d8751d241405fb").orElseThrow();
        KnownServerSource source = Mockito.mock(KnownServerSource.class);
        PromiseFuture<Void> delay = PromiseFuture.newPromiseFuture(_ -> {});
        ServerLookup lookup = lookup(source, _ -> delay,
                _ -> { throw new AssertionError("No address lookup expected"); });

        PromiseFuture<MysterServer> result = lookup.start(candidate, _ -> {});
        result.cancel();

        assertTrue(delay.isCancelled());
        Mockito.verifyNoInteractions(source);
    }

    @Test
    void cancellationDuringCidResolutionCancelsTheSourceRequest() {
        var candidate = (ServerLookupCandidate.Cid) ServerLookupCandidate.parse(
                "46e22ea215589a03f3d8751d241405fb").orElseThrow();
        KnownServerSource source = Mockito.mock(KnownServerSource.class);
        PromiseFuture<MysterServer> resolution = PromiseFuture.newPromiseFuture(_ -> {});
        Mockito.when(source.resolveServerCid(candidate.cid())).thenReturn(resolution);
        ServerLookup lookup = lookup(source,
                _ -> PromiseFuture.newPromiseFuture((Void) null),
                _ -> { throw new AssertionError("No address lookup expected"); });

        PromiseFuture<MysterServer> result = lookup.start(candidate, _ -> {});
        result.cancel();

        assertTrue(resolution.isCancelled());
    }

    private static ServerLookup lookup(
            KnownServerSource source,
            java.util.function.Function<Duration, PromiseFuture<Void>> delayFactory,
            ServerLookup.AddressResolver resolver) {
        return new ServerLookup(source,
                Duration.ofSeconds(1), delayFactory, resolver, Invoker.SYNCHRONOUS);
    }

    private static KnownServerSource source(AtomicReference<MysterAddress> requestedAddress,
                                            CountDownLatch requested,
                                            PromiseFuture<MysterServer> result) {
        return source(address -> {
            requestedAddress.set(address);
            requested.countDown();
        }, result);
    }

    private static KnownServerSource source(Consumer<MysterAddress> onResolve,
                                            PromiseFuture<MysterServer> result) {
        return new KnownServerSource() {
            @Override
            public void forEachServer(Consumer<MysterServer> consumer) {}

            @Override
            public Optional<String> resolveDisplayName(ServerCid cid) {
                return Optional.empty();
            }

            @Override
            public PromiseFuture<MysterServer> resolveServer(MysterAddress address) {
                onResolve.accept(address);
                return result;
            }

            @Override
            public PromiseFuture<MysterServer> resolveServerCid(ServerCid cid) {
                throw new AssertionError("Address input must not use 3DNS");
            }
        };
    }

    private static MysterAddress loopback() {
        return new MysterAddress(InetAddress.getLoopbackAddress());
    }

    private static void await(CountDownLatch latch) {
        boolean interrupted = false;
        while (true) {
            try {
                latch.await();
                break;
            } catch (InterruptedException _) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}

package com.myster.net.stream.client.msdownload;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Logger;

import com.general.thread.Cancellable;
import com.general.thread.PromiseFuture;
import com.myster.cid.ServerCid;
import com.myster.net.client.DnsLookupProtocol;
import com.myster.threedns.ThreeDnsLookupResult;
import com.myster.threedns.VerifiedThreeDnsPeer;

/**
 * A one-shot recovery operation. Reads saved sources on a worker and starts an independent worker
 * for every CID. Each worker bounds DNS by a deadline and submits an exact match to MultiSourceDownload.
 * Recovery does not wait for connection setup or own the submitted downloaders. Cancellation
 * interrupts lookup workers; MultiSourceDownload owns transfer cancellation and each transport
 * enforces its own timeouts. The DNS promise is only a boundary to the existing resolver API.
 * Expected I/O, timeout and cancellation failures leave that source unavailable; unexpected
 * failures propagate to the worker's uncaught-exception handler.
 */
final class DownloadSourceRecovery implements Cancellable {
    private static final Logger LOG = Logger.getLogger(DownloadSourceRecovery.class.getName());
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final Supplier<List<ServerCid>> sources;
    private final DnsLookupProtocol dns;
    private final Consumer<VerifiedThreeDnsPeer> candidate;
    private final Duration timeout;
    private boolean started;
    private boolean cancelled;

    DownloadSourceRecovery(Supplier<List<ServerCid>> sources, DnsLookupProtocol dns,
                           Consumer<VerifiedThreeDnsPeer> candidate) {
        this(sources, dns, candidate, Duration.ofSeconds(30));
    }

    DownloadSourceRecovery(Supplier<List<ServerCid>> sources,
                           DnsLookupProtocol dns,
                           Consumer<VerifiedThreeDnsPeer> candidate,
                           Duration timeout) {
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("Recovery timeout must be positive");
        }
        this.sources = sources;
        this.dns = dns;
        this.candidate = candidate;
        this.timeout = timeout;
    }

    /** Starts once, returning immediately. A cancelled operation cannot be restarted. */
    synchronized void start() {
        if (started || cancelled) {
            return;
        }
        started = true;
        workers.execute(this::readSources);
    }

    private void readSources() {
        try {
            for (ServerCid cid : sources.get()) {
                synchronized (this) {
                    if (cancelled) {
                        return;
                    }
                    workers.execute(() -> recover(cid));
                }
            }
        } finally {
            workers.shutdown();
        }
    }

    private void recover(ServerCid cid) {
        long startedAt = System.nanoTime();
        PromiseFuture<ThreeDnsLookupResult> lookup = null;
        try {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            lookup = dns.resolve(cid);
            ThreeDnsLookupResult result = lookup.get(remaining(startedAt), TimeUnit.NANOSECONDS);
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            if (result.exactPeer().isEmpty() || !result.exactPeer().get().cid().equals(cid)) {
                return;
            }
            candidate.accept(result.exactPeer().get());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException || cause instanceof TimeoutException
                    || cause instanceof CancellationException) {
                LOG.fine("Download source unavailable: " + cause);
            } else if (cause instanceof RuntimeException failure) {
                throw failure;
            } else if (cause instanceof Error failure) {
                throw failure;
            } else {
                throw new IllegalStateException("Unexpected DNS lookup failure", cause);
            }
        } catch (TimeoutException | CancellationException e) {
            LOG.fine("Download source unavailable: " + e);
        } finally {
            if (lookup != null && !lookup.isDone()) {
                lookup.cancel();
            }
        }
    }

    private long remaining(long startedAt) throws TimeoutException {
        long remaining = timeout.toNanos() - (System.nanoTime() - startedAt);
        if (remaining <= 0) {
            throw new TimeoutException("Source recovery timed out");
        }
        return remaining;
    }

    /** Requests worker interruption. Pending DNS lookups are cancelled as their workers unwind. */
    @Override
    public synchronized void cancel() {
        cancelled = true;
        workers.shutdownNow();
    }

    /** True once all source reads, lookups and candidate callbacks have finished. */
    boolean isDone() {
        return workers.isTerminated();
    }

    /** Waits for worker cleanup; must not be called from the EDT. */
    boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return workers.awaitTermination(timeout, unit);
    }
}

package com.myster.tracker.ui;

import java.util.Optional;
import java.util.function.Consumer;

import com.general.thread.PromiseFuture;
import com.myster.cid.ServerCid;
import com.myster.net.MysterAddress;
import com.myster.tracker.MysterServer;

/**
 * Fast UI-facing view of locally known servers plus explicit address resolution.
 *
 * <p>Enumeration is non-blocking and does not apply connection- or membership-specific policy;
 * each UI caller supplies its own eligibility rule. Explicit resolution performs the normal
 * server-pool refresh and returns the pool-owned server produced from server stats.
 */
public interface KnownServerSource {
    /**
     * Visits a snapshot of every locally known server without performing network work.
     *
     * @param consumer receives each known server and must return quickly
     */
    void forEachServer(Consumer<MysterServer> consumer);

    /**
     * Resolves a stored member identity to its current friendly server name.
     *
     * @param cid public-key-backed server identity
     * @return a non-null display name, or empty when unavailable
     */
    Optional<String> resolveDisplayName(ServerCid cid);

    /**
     * Explicitly refreshes a user-entered address through server stats.
     * Each call returns a caller-owned future; cancellation does not affect other callers
     * even when the implementation shares an underlying pool refresh.
     *
     * @param address already DNS-resolved Myster address
     * @return independently cancellable future completed with the pool's refreshed server
     */
    PromiseFuture<MysterServer> resolveServer(MysterAddress address);
}

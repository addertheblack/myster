package com.myster.threedns;

import java.util.Objects;
import java.util.Optional;

import com.myster.cid.ServerCid;
import com.myster.net.MysterAddress;

/**
 * Immutable terminal result of an iterative 3DNS lookup.
 *
 * <p>Only {@link Status#EXACT_VERIFIED} exposes an exact peer or exact address.
 * Limit and deadline results may retain the closest verified peer reached
 * before the bound ended the lookup.
 */
public final class ThreeDnsLookupResult {
    public enum Status {
        EXACT_VERIFIED,
        CLOSEST_VERIFIED,
        NO_ROUTE,
        QUERY_LIMIT_REACHED,
        DEADLINE_REACHED
    }

    private final Status status;
    private final ServerCid target;
    private final Optional<VerifiedThreeDnsPeer> exactPeer;
    private final Optional<VerifiedThreeDnsPeer> closestPeer;

    private ThreeDnsLookupResult(Status status,
                                 ServerCid target,
                                 Optional<VerifiedThreeDnsPeer> exactPeer,
                                 Optional<VerifiedThreeDnsPeer> closestPeer) {
        this.status = Objects.requireNonNull(status, "status");
        this.target = Objects.requireNonNull(target, "target");
        this.exactPeer = Objects.requireNonNull(exactPeer, "exactPeer");
        this.closestPeer = Objects.requireNonNull(closestPeer, "closestPeer");
        validate();
    }

    static ThreeDnsLookupResult exact(ServerCid target, VerifiedThreeDnsPeer peer) {
        return new ThreeDnsLookupResult(Status.EXACT_VERIFIED,
                                        target,
                                        Optional.of(peer),
                                        Optional.of(peer));
    }

    static ThreeDnsLookupResult exhausted(ServerCid target,
                                           Optional<VerifiedThreeDnsPeer> closestPeer) {
        Status status = closestPeer.isPresent() ? Status.CLOSEST_VERIFIED : Status.NO_ROUTE;
        return bounded(status, target, closestPeer);
    }

    static ThreeDnsLookupResult bounded(Status status,
                                        ServerCid target,
                                        Optional<VerifiedThreeDnsPeer> closestPeer) {
        return new ThreeDnsLookupResult(status, target, Optional.empty(), closestPeer);
    }

    private void validate() {
        if (status == Status.EXACT_VERIFIED) {
            if (exactPeer.isEmpty() || closestPeer.isEmpty()
                    || !exactPeer.get().equals(closestPeer.get())
                    || !exactPeer.get().cid().equals(target)) {
                throw new IllegalArgumentException("Exact lookup result requires the verified target peer");
            }
            return;
        }
        if (exactPeer.isPresent()) {
            throw new IllegalArgumentException("Only exact success may expose an exact peer");
        }
        if (status == Status.CLOSEST_VERIFIED && closestPeer.isEmpty()) {
            throw new IllegalArgumentException("Closest result requires a verified peer");
        }
        if (status == Status.NO_ROUTE && closestPeer.isPresent()) {
            throw new IllegalArgumentException("No-route result cannot contain a verified peer");
        }
    }

    public Status status() {
        return status;
    }

    public ServerCid target() {
        return target;
    }

    /** @return the target peer only after an authenticated exact response */
    public Optional<VerifiedThreeDnsPeer> exactPeer() {
        return exactPeer;
    }

    /** @return the best authenticated peer reached, when one exists */
    public Optional<VerifiedThreeDnsPeer> closestPeer() {
        return closestPeer;
    }

    /** @return the target address only after an authenticated exact response */
    public Optional<MysterAddress> exactAddress() {
        return exactPeer.map(VerifiedThreeDnsPeer::address);
    }

    @Override
    public String toString() {
        return "ThreeDnsLookupResult[status=" + status
                + ", target=" + target
                + ", exactPeer=" + exactPeer
                + ", closestPeer=" + closestPeer + "]";
    }
}

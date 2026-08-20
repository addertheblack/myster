package com.myster.threedns;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

import com.myster.cid.ServerCid;
import com.myster.net.MysterAddress;
import com.myster.tracker.PublicKeyIdentity;

/** Maintains bounded, deterministic candidate ordering for one lookup. */
final class ThreeDnsLookupFrontier {
    private final ServerCid target;
    private final int maxQueuedEntries;
    // Different peers may advertise different addresses for the same identity. Allow a bounded
    // alternate after a stale-address failure without letting one identity consume the frontier.
    private final int maxAddressesPerIdentity;
    private final PriorityQueue<ThreeDnsAddressCandidate> queued;
    private final Map<PublicKeyIdentity, IdentityState> identities = new HashMap<>();

    ThreeDnsLookupFrontier(ServerCid target,
                           int maxQueuedEntries,
                           int maxAddressesPerIdentity) {
        this.target = Objects.requireNonNull(target, "target");
        if (maxQueuedEntries <= 0) {
            throw new IllegalArgumentException("maxQueuedEntries must be positive");
        }
        if (maxAddressesPerIdentity <= 0) {
            throw new IllegalArgumentException("maxAddressesPerIdentity must be positive");
        }
        this.maxQueuedEntries = maxQueuedEntries;
        this.maxAddressesPerIdentity = maxAddressesPerIdentity;
        queued = new PriorityQueue<>(this::compareCandidates);
    }

    void addAll(ThreeDnsAddressCandidateSet candidates) {
        Objects.requireNonNull(candidates, "candidates");
        candidates.exact().ifPresent(this::add);
        candidates.left().forEach(this::add);
        candidates.right().forEach(this::add);
    }

    /**
     * Retains the candidate when it survives deduplication and the queued-entry bound.
     *
     * @return whether the candidate was retained; production ingestion ignores this value and it
     *         is exposed only for focused frontier tests
     */
    boolean add(ThreeDnsAddressCandidate candidate) {
        Objects.requireNonNull(candidate, "candidate");
        IdentityState state = identities.computeIfAbsent(candidate.identity(), _ -> new IdentityState());
        if (state.verified
                || state.attemptedAddresses.contains(candidate.address())
                || state.knownAddresses.contains(candidate.address())
                || state.knownAddresses.size() >= maxAddressesPerIdentity) {
            return false;
        }

        state.knownAddresses.add(candidate.address());
        queued.add(candidate);
        if (queued.size() <= maxQueuedEntries) {
            return true;
        }

        ThreeDnsAddressCandidate worst = queued.stream()
                .max(this::compareCandidates)
                .orElseThrow();

        queued.remove(worst);
        identities.get(worst.identity()).knownAddresses.remove(worst.address());
        return worst != candidate;
    }

    // Package protected for unit tests
    Optional<ThreeDnsAddressCandidate> pollEligible(Optional<ServerCid> closestVerifiedCid) {
        List<ThreeDnsAddressCandidate> deferred = new ArrayList<>();
        Optional<ThreeDnsAddressCandidate> selected = removeNextEligible(
                closestVerifiedCid,
                deferred);

        // This can only happen if there's two CIDs that have different addresses
        queued.addAll(deferred);

        return selected;
    }

    private Optional<ThreeDnsAddressCandidate> removeNextEligible(
            Optional<ServerCid> closestVerifiedCid,
            List<ThreeDnsAddressCandidate> deferred) {
        while (!queued.isEmpty()) {
            ThreeDnsAddressCandidate candidate = queued.remove();
            IdentityState state = identities.get(candidate.identity());

            if (state.verified || state.attemptedAddresses.contains(candidate.address())) {
                continue;
            }

            if (!isStrictProgress(candidate, closestVerifiedCid)) {
                continue;
            }

            // Should be rare - it's if there's two cids with different addresses.
            if (state.inFlight) {
                deferred.add(candidate);
                continue;
            }

            return Optional.of(candidate);
        }
        return Optional.empty();
    }

    boolean hasEligible(Optional<ServerCid> closestVerifiedCid) {
        for (ThreeDnsAddressCandidate candidate : queued) {
            IdentityState state = identities.get(candidate.identity());
            if (!state.verified
                    && !state.inFlight
                    && !state.attemptedAddresses.contains(candidate.address())
                    && isStrictProgress(candidate, closestVerifiedCid)) {
                return true;
            }
        }
        return false;
    }

    void markLaunched(ThreeDnsAddressCandidate candidate) {
        IdentityState state = identities.get(candidate.identity());
        if (state == null || state.verified || state.inFlight
                || !state.knownAddresses.contains(candidate.address())) {
            throw new IllegalStateException("Candidate is not launchable");
        }

        state.inFlight = true;
        state.attemptedAddresses.add(candidate.address());
    }

    void markFailed(ThreeDnsAddressCandidate candidate) {
        IdentityState state = identities.get(candidate.identity());
        if (state != null) {
            state.inFlight = false;
        }
    }

    void markVerified(ThreeDnsAddressCandidate candidate) {
        IdentityState state = identities.get(candidate.identity());
        if (state == null) {
            throw new IllegalStateException("Verified candidate was not known to the frontier");
        }

        state.inFlight = false;
        state.verified = true;
        queued.removeIf(queuedCandidate -> queuedCandidate.identity().equals(candidate.identity()));
    }

    int queuedCount() {
        return queued.size();
    }

    private boolean isStrictProgress(ThreeDnsAddressCandidate candidate,
                                     Optional<ServerCid> closestVerifiedCid) {
        return candidate.cid().equals(target)
                || closestVerifiedCid.isEmpty()
                || target.comparePredecessorDistance(candidate.cid(), closestVerifiedCid.get()) < 0;
    }

    private int compareCandidates(ThreeDnsAddressCandidate first,
                                  ThreeDnsAddressCandidate second) {
        int distance = target.comparePredecessorDistance(first.cid(), second.cid());
        if (distance != 0) {
            return distance;
        }
        int identity = Arrays.compareUnsigned(first.identity().getPublicKey().getEncoded(),
                                              second.identity().getPublicKey().getEncoded());
        if (identity != 0) {
            return identity;
        }
        int address = Arrays.compareUnsigned(first.address().getInetAddress().getAddress(),
                                             second.address().getInetAddress().getAddress());
        if (address != 0) {
            return address;
        }
        return Integer.compare(first.address().getPort(), second.address().getPort());
    }

    private static final class IdentityState {
        private final Set<MysterAddress> knownAddresses = new LinkedHashSet<>();
        private final Set<MysterAddress> attemptedAddresses = new HashSet<>();
        private boolean inFlight;
        private boolean verified;
    }
}

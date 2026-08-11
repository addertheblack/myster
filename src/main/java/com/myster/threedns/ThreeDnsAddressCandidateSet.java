package com.myster.threedns;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable result of one 3DNS {@code FIND_CLOSEST} request. Left contains
 * predecessor-side candidates and right contains successor-side candidates;
 * either side may be sparse.
 */
public record ThreeDnsAddressCandidateSet(
    Optional<ThreeDnsAddressCandidate> exact,
    List<ThreeDnsAddressCandidate> left,
    List<ThreeDnsAddressCandidate> right
) {
    public static final int SCHEMA_VERSION = 1;
    public static final int DEFAULT_PER_SIDE_LIMIT = 2;
    public static final int MAX_PER_SIDE_LIMIT = 4;
    public static final int MAX_RESPONSE_BYTES = 16 * 1024;

    public ThreeDnsAddressCandidateSet {
        Objects.requireNonNull(exact, "exact");
        left = List.copyOf(left);
        right = List.copyOf(right);
    }

    public static ThreeDnsAddressCandidateSet empty() {
        return new ThreeDnsAddressCandidateSet(Optional.empty(), List.of(), List.of());
    }

    /**
     * Applies the wire contract's default and upper bound.
     *
     * @param requestedLimit requested candidates for each directional group
     * @return two for a non-positive request, otherwise at most four
     */
    public static int normalizePerSideLimit(int requestedLimit) {
        if (requestedLimit <= 0) {
            return DEFAULT_PER_SIDE_LIMIT;
        }
        return Math.min(requestedLimit, MAX_PER_SIDE_LIMIT);
    }
}

package com.myster.threedns;

import java.util.Objects;

/**
 * Immutable result of one expected-key 3DNS query.
 *
 * <p>Only {@link #responder()} is verified. Every entry in
 * {@link #candidates()} remains an untrusted routing hint until it independently
 * completes an expected-key operation.
 */
public final class ThreeDnsVerifiedQueryResult {
    private final VerifiedThreeDnsPeer responder;
    private final ThreeDnsAddressCandidateSet candidates;

    ThreeDnsVerifiedQueryResult(VerifiedThreeDnsPeer responder,
                                ThreeDnsAddressCandidateSet candidates) {
        this.responder = Objects.requireNonNull(responder, "responder");
        this.candidates = Objects.requireNonNull(candidates, "candidates");
    }

    public VerifiedThreeDnsPeer responder() {
        return responder;
    }

    public ThreeDnsAddressCandidateSet candidates() {
        return candidates;
    }
}

package com.myster.tracker;

import java.util.List;
import java.util.Optional;

import org.msgpack.core.Preconditions;

/**
 * Pool-level closest-CID result split by exact match and directional ring
 * neighbors. Left means predecessor-side candidates and right means
 * successor-side candidates in unsigned CID order. Either side may contain
 * fewer entries than requested when the pool has too few currently usable
 * public-key identities.
 */
public record IdentityNeighborSet(
    Optional<PublicKeyIdentity> exact,
    List<PublicKeyIdentity> left,
    List<PublicKeyIdentity> right
) {
    public IdentityNeighborSet {
        Preconditions.checkNotNull(exact, "exact must not be null; use Optional.empty() for no exact match");
        left = List.copyOf(left);
        right = List.copyOf(right);
    }

    public static IdentityNeighborSet empty() {
        return new IdentityNeighborSet(Optional.empty(), List.of(), List.of());
    }
}

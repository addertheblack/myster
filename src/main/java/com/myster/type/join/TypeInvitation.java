package com.myster.type.join;

import java.util.Objects;
import java.util.Optional;

import com.myster.cid.ServerCid;
import com.myster.type.MysterType;

/**
 * Immutable local invitation record. It contains a password verifier, never the password itself.
 * The type and invitation id are represented by Preferences node names when persisted.
 */
public final class TypeInvitation {
    private final MysterType type;
    private final byte[] invitationId;
    private final String algorithm;
    private final int iterations;
    private final byte[] salt;
    private final byte[] verifier;
    private final long createdAt;
    private final long expiresAt;
    private final Optional<ServerCid> claimedBy;
    private final boolean redemptionComplete;

    public TypeInvitation(MysterType type, byte[] invitationId, String algorithm, int iterations,
            byte[] salt, byte[] verifier, long createdAt, long expiresAt,
            Optional<ServerCid> claimedBy, boolean redemptionComplete) {
        this.type = Objects.requireNonNull(type, "type");
        if (Objects.requireNonNull(invitationId, "invitationId").length
                != TypeJoinUri.INVITATION_ID_BYTES) {
            throw new IllegalArgumentException("Invitation id must be 16 bytes");
        }
        this.invitationId = invitationId.clone();
        this.algorithm = Objects.requireNonNull(algorithm, "algorithm");
        this.iterations = iterations;
        this.salt = Objects.requireNonNull(salt, "salt").clone();
        this.verifier = Objects.requireNonNull(verifier, "verifier").clone();
        if (expiresAt <= createdAt) {
            throw new IllegalArgumentException("Invitation expiry must follow creation");
        }
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.claimedBy = Objects.requireNonNull(claimedBy, "claimedBy");
        if (redemptionComplete && claimedBy.isEmpty()) {
            throw new IllegalArgumentException("Completed redemption requires a caller identity");
        }
        this.redemptionComplete = redemptionComplete;
        InvitationPasswordVerifier.validateParameters(algorithm, iterations, this.salt, this.verifier);
    }

    public MysterType type() { return type; }
    public byte[] invitationId() { return invitationId.clone(); }
    public String algorithm() { return algorithm; }
    public int iterations() { return iterations; }
    public byte[] salt() { return salt.clone(); }
    public byte[] verifier() { return verifier.clone(); }
    /** @return creation time as UTC epoch milliseconds */
    public long createdAt() { return createdAt; }
    /** @return expiry time as UTC epoch milliseconds */
    public long expiresAt() { return expiresAt; }
    public Optional<ServerCid> claimedBy() { return claimedBy; }
    public boolean redemptionComplete() { return redemptionComplete; }

    public boolean isExpired(long nowEpochMillis) {
        return nowEpochMillis >= expiresAt;
    }

    public TypeInvitation claimedBy(ServerCid caller) {
        return new TypeInvitation(type, invitationId, algorithm, iterations, salt, verifier,
                createdAt, expiresAt, Optional.of(caller), false);
    }

    public TypeInvitation completed() {
        if (claimedBy.isEmpty()) {
            throw new IllegalStateException("Unclaimed invitation cannot be completed");
        }
        return new TypeInvitation(type, invitationId, algorithm, iterations, salt, verifier,
                createdAt, expiresAt, claimedBy, true);
    }
}

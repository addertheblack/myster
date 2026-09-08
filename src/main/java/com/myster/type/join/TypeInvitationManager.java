package com.myster.type.join;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.prefs.BackingStoreException;

import com.myster.cid.ServerCid;
import com.myster.type.MysterType;
import com.myster.type.join.TypeMembershipService.AddResult;
import com.myster.type.join.TypeMembershipService.Authorization;
import com.myster.type.join.TypeMembershipService.MembershipException;

/**
 * Creates and redeems local, single-use type invitations.
 *
 * <p>Password verification occurs outside the type mutation lock. The record is then reloaded and
 * claimed inside the membership lock before the signed access-list append. This makes crashes and
 * lost responses resumable only by the authenticated identity that won the claim.
 */
public final class TypeInvitationManager {
    private final TypeInvitationStore store;
    private final InvitationPasswordVerifier passwordVerifier;
    private final InvitationAttemptLimiter limiter;
    private final TypeMembershipService membershipService;
    private final SecureRandom random;
    private final Clock clock;

    public TypeInvitationManager(TypeInvitationStore store,
            InvitationPasswordVerifier passwordVerifier,
            InvitationAttemptLimiter limiter,
            TypeMembershipService membershipService,
            SecureRandom random,
            Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.passwordVerifier = Objects.requireNonNull(passwordVerifier, "passwordVerifier");
        this.limiter = Objects.requireNonNull(limiter, "limiter");
        this.membershipService = Objects.requireNonNull(membershipService, "membershipService");
        this.random = Objects.requireNonNull(random, "random");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Creates and durably flushes an invitation before returning it.
     *
     * @throws IOException if the type is absent, this node cannot authorize it, or KDF setup fails
     * @throws BackingStoreException if the invitation cannot be durably stored
     */
    public TypeInvitation create(MysterType type, char[] password, InvitationLifetime lifetime)
            throws IOException, BackingStoreException {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(lifetime, "lifetime");
        Authorization authorization = membershipService.authorization(type);
        if (authorization != Authorization.AUTHORIZED) {
            throw new MembershipException(authorization);
        }

        InvitationPasswordVerifier.Material material;
        try {
            material = passwordVerifier.create(password);
        } catch (GeneralSecurityException exception) {
            throw new IOException("Could not derive invitation verifier", exception);
        }
        byte[] invitationId = new byte[TypeJoinUri.INVITATION_ID_BYTES];
        random.nextBytes(invitationId);
        long createdAt = clock.millis();
        long expiresAt = Math.addExact(createdAt, lifetime.duration().toMillis());
        TypeInvitation invitation = new TypeInvitation(type, invitationId, material.algorithm(),
                material.iterations(), material.salt(), material.verifier(), createdAt, expiresAt,
                Optional.empty(), false);
        store.create(invitation);
        return invitation;
    }

    /** Redeems an invitation for the TLS-authenticated caller without revealing record state. */
    public TypeJoinStatus redeem(MysterType type, byte[] invitationId, char[] password,
            ServerCid caller, String remoteAddress) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(caller, "caller");
        Objects.requireNonNull(remoteAddress, "remoteAddress");
        try {
            InvitationPasswordVerifier.validatePassword(password);
            Optional<TypeInvitation> loaded = store.load(type, invitationId);
            if (loaded.isEmpty() || !limiter.isAllowed(invitationId, caller, remoteAddress)
                    || !limiter.tryAcquireVerification()) {
                return TypeJoinStatus.INVITATION_NOT_ACCEPTED;
            }

            boolean matches;
            try {
                matches = passwordVerifier.matches(password, loaded.get());
            } finally {
                limiter.releaseVerification();
            }
            if (!matches) {
                limiter.recordFailure(invitationId, caller, remoteAddress);
                return TypeJoinStatus.INVITATION_NOT_ACCEPTED;
            }
            limiter.recordSuccess(invitationId, caller, remoteAddress);

            return membershipService.withTypeLock(type,
                    () -> redeemUnderLock(type, invitationId, caller));
        } catch (MembershipException exception) {
            return exception.authorization() == Authorization.TYPE_NOT_FOUND
                    ? TypeJoinStatus.TYPE_NOT_FOUND : TypeJoinStatus.NOT_AUTHORIZER;
        } catch (IllegalArgumentException exception) {
            return TypeJoinStatus.INVITATION_NOT_ACCEPTED;
        } catch (BackingStoreException | IOException | GeneralSecurityException exception) {
            return TypeJoinStatus.ERROR;
        }
    }

    private TypeJoinStatus redeemUnderLock(MysterType type, byte[] invitationId, ServerCid caller)
            throws IOException, BackingStoreException {
        Authorization authorization = membershipService.authorization(type);
        if (authorization != Authorization.AUTHORIZED) {
            throw new MembershipException(authorization);
        }
        TypeInvitation current = store.load(type, invitationId).orElse(null);
        if (current == null || current.isExpired(clock.millis())) {
            return TypeJoinStatus.INVITATION_NOT_ACCEPTED;
        }
        if (current.claimedBy().isPresent() && !current.claimedBy().orElseThrow().equals(caller)) {
            return TypeJoinStatus.INVITATION_NOT_ACCEPTED;
        }
        if (current.redemptionComplete()) {
            return membershipService.isMember(type, caller)
                    ? TypeJoinStatus.ALREADY_MEMBER
                    : TypeJoinStatus.INVITATION_NOT_ACCEPTED;
        }

        if (current.claimedBy().isEmpty()) {
            current = current.claimedBy(caller);
            store.update(current);
        }

        AddResult addResult = membershipService.addMember(type, caller);
        store.update(current.completed());
        return addResult == AddResult.ADDED
                ? TypeJoinStatus.APPROVED : TypeJoinStatus.ALREADY_MEMBER;
    }
}

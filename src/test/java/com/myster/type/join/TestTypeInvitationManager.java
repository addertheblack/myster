package com.myster.type.join;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.Preferences;

import com.myster.type.MetadataTypeId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.myster.access.AccessList;
import com.myster.access.AccessListManager;
import com.myster.access.Policy;
import com.myster.cid.ServerCid;

class TestTypeInvitationManager {
    private Preferences preferences;

    @BeforeEach
    void setUp() {
        preferences = Preferences.userRoot().node(
                "MysterTest/TypeInvitationManager/" + System.nanoTime());
    }

    @AfterEach
    void tearDown() throws Exception {
        preferences.removeNode();
        preferences.flush();
    }

    @Test
    void redemptionIsSingleIdentityAndRetryIdempotent() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-01-02T03:04:05Z"), ZoneOffset.UTC);
        KeyPair writer = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        KeyPair typeIdentity = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        AccessList genesis = AccessList.createGenesis(typeIdentity.getPublic(), writer, List.of(), List.of(), Policy.defaultRestrictive(),
                "Private", "", new String[]{"dat"}, false, MetadataTypeId.GENERIC);
        AtomicReference<AccessList> stored = new AtomicReference<>(genesis);
        AccessListManager accessLists = mock(AccessListManager.class);
        when(accessLists.loadAccessList(genesis.getMysterType()))
                .thenAnswer(ignored -> Optional.of(stored.get()));
        doAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return null;
        }).when(accessLists).saveAccessList(any(AccessList.class));

        TypeMembershipService membership = new TypeMembershipService(accessLists,
                ignored -> Optional.of(writer));
        InvitationPasswordVerifier verifier = new InvitationPasswordVerifier(
                new SecureRandom(), new InvitationPasswordVerifier.Policy(1));
        TypeInvitationManager invitations = new TypeInvitationManager(
                new TypeInvitationStore(preferences, clock), verifier,
                new InvitationAttemptLimiter(1, clock), membership, new SecureRandom(), clock);

        char[] password = "separate secret".toCharArray();
        TypeInvitation invitation = invitations.create(
                genesis.getMysterType(), password, InvitationLifetime.SEVEN_DAYS);
        ServerCid recipient = newServerCid();
        ServerCid stranger = newServerCid();

        assertEquals(TypeJoinStatus.APPROVED, invitations.redeem(genesis.getMysterType(),
                invitation.invitationId(), password, recipient, "192.0.2.1"));
        assertTrue(stored.get().getState().isMember(recipient));
        assertEquals(2, stored.get().getBlocks().size());

        assertEquals(TypeJoinStatus.ALREADY_MEMBER, invitations.redeem(genesis.getMysterType(),
                invitation.invitationId(), password, recipient, "192.0.2.1"));
        assertEquals(2, stored.get().getBlocks().size());
        assertEquals(TypeJoinStatus.INVITATION_NOT_ACCEPTED,
                invitations.redeem(genesis.getMysterType(), invitation.invitationId(), password,
                        stranger, "192.0.2.2"));
        assertEquals(2, stored.get().getBlocks().size());
    }

    private static ServerCid newServerCid() throws Exception {
        return ServerCid.fromPublicKey(
                KeyPairGenerator.getInstance("RSA").generateKeyPair().getPublic());
    }
}

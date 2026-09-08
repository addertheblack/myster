package com.myster.type.join;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.myster.cid.ServerCid;
import com.myster.type.MysterType;

class TestTypeInvitationStore {
    private static final long NOW = 1_000_000L;
    private Preferences root;
    private TypeInvitationStore store;
    private MysterType type;

    @BeforeEach
    void setUp() {
        root = Preferences.userRoot().node("MysterTest/Invitations/" + System.nanoTime());
        store = new TypeInvitationStore(root,
                Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC));
        type = new MysterType(bytes(1));
    }

    @AfterEach
    void tearDown() throws Exception {
        root.removeNode();
        root.flush();
    }

    @Test
    void writesExactNodeSchemaAndRoundTrips() throws Exception {
        TypeInvitation invitation = invitation(2, NOW + 1000);
        store.create(invitation);

        Preferences node = root.node(type.toHexString())
                .node(java.util.HexFormat.of().formatHex(invitation.invitationId()));
        assertEquals(1, node.getInt("schemaVersion", -1));
        assertEquals(InvitationPasswordVerifier.ALGORITHM, node.get("kdfAlgorithm", null));
        assertArrayEquals(invitation.salt(), node.getByteArray("salt", null));
        assertFalse(node.getBoolean("redemptionComplete", true));
        assertArrayEquals(invitation.verifier(), store.load(type, invitation.invitationId())
                .orElseThrow().verifier());
    }

    @Test
    void claimAndCompletionPersistCaller() throws Exception {
        TypeInvitation invitation = invitation(3, NOW + 1000);
        store.create(invitation);
        ServerCid caller = new ServerCid(bytes(9));
        store.update(invitation.claimedBy(caller));
        assertEquals(caller, store.load(type, invitation.invitationId())
                .orElseThrow().claimedBy().orElseThrow());
        store.update(invitation.claimedBy(caller).completed());
        assertTrue(store.load(type, invitation.invitationId()).orElseThrow().redemptionComplete());
    }

    @Test
    void expiredRecordIsRemovedAtBoundary() throws Exception {
        TypeInvitation expired = invitation(4, NOW);
        store.create(expired);
        assertTrue(store.load(type, expired.invitationId()).isEmpty());
        assertFalse(root.node(type.toHexString()).nodeExists(
                java.util.HexFormat.of().formatHex(expired.invitationId())));
    }

    @Test
    void malformedKnownVersionIsRemovedButFutureVersionIsRetained() throws Exception {
        Preferences typeNode = root.node(type.toHexString());
        String malformedName = java.util.HexFormat.of().formatHex(bytes(5));
        typeNode.node(malformedName).putInt("schemaVersion", 1);
        String futureName = java.util.HexFormat.of().formatHex(bytes(6));
        typeNode.node(futureName).putInt("schemaVersion", 2);
        typeNode.flush();

        assertTrue(store.list(type).isEmpty());
        assertFalse(typeNode.nodeExists(malformedName));
        assertTrue(typeNode.nodeExists(futureName));
    }

    @Test
    void enforcesSimplePerTypeBound() throws Exception {
        for (int i = 0; i < TypeInvitationStore.MAX_UNEXPIRED_PER_TYPE; i++) {
            store.create(invitation(i, NOW + 1000));
        }
        assertThrows(IllegalStateException.class,
                () -> store.create(invitation(100, NOW + 1000)));
    }

    private TypeInvitation invitation(int seed, long expiresAt) {
        return new TypeInvitation(type, bytes(seed), InvitationPasswordVerifier.ALGORITHM, 100,
                new byte[16], new byte[32], NOW - 1, expiresAt, Optional.empty(), false);
    }

    private static byte[] bytes(int seed) {
        byte[] result = new byte[16];
        result[15] = (byte) seed;
        return result;
    }
}

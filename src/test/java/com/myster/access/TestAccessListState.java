package com.myster.access;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;

import com.myster.identity.Cid128;
import com.myster.type.MysterType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link AccessListState} state derivation from operations.
 */
class TestAccessListState {

    private static KeyPair ed25519KeyPair;
    private static KeyPair rsaKeyPair;

    @BeforeAll
    static void generateKeys() throws Exception {
        ed25519KeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        rsaKeyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
    }

    @Test
    void emptyStateHasDefaults() {
        AccessListState state = new AccessListState();
        assertFalse(state.getPolicy().isListFilesPublic());
        assertEquals(0, state.getWriters().size());
        assertEquals(0, state.getMembers().size());
        assertEquals(0, state.getOnramps().size());
        assertNull(state.getTypePublicKey());
        assertNull(state.getName());
        assertNull(state.getDescription());
        assertArrayEquals(new String[0], state.getExtensions());
        assertFalse(state.isSearchInArchives());
        assertEquals(-1, state.getHeight());
    }

    @Test
    void applyMetadataOperations() {
        AccessListState state = new AccessListState();
        PublicKey writer = ed25519KeyPair.getPublic();
        state.applyOperation(new AddWriterOp(writer), writer);

        state.applyOperation(new SetTypePublicKeyOp(rsaKeyPair.getPublic()), writer);
        state.applyOperation(new SetNameOp("Test Type"), writer);
        state.applyOperation(new SetDescriptionOp("A test description"), writer);
        state.applyOperation(new SetExtensionsOp(new String[]{"mp3", "wav"}), writer);
        state.applyOperation(new SetSearchInArchivesOp(true), writer);

        assertArrayEquals(rsaKeyPair.getPublic().getEncoded(),
                          state.getTypePublicKey().getEncoded());
        assertEquals("Test Type", state.getName());
        assertEquals("A test description", state.getDescription());
        assertArrayEquals(new String[]{"mp3", "wav"}, state.getExtensions());
        assertTrue(state.isSearchInArchives());
    }

    @Test
    void applyMemberOperations() {
        AccessListState state = new AccessListState();
        PublicKey writer = ed25519KeyPair.getPublic();
        state.applyOperation(new AddWriterOp(writer), writer);

        Cid128 cid = com.myster.identity.Util.generateCid(rsaKeyPair.getPublic());
        state.applyOperation(new AddMemberOp(cid, Role.MEMBER), writer);
        assertTrue(state.isMember(cid));
        assertEquals(Role.MEMBER, state.getRole(cid));
        assertFalse(state.isAdmin(cid));

        state.applyOperation(new RemoveMemberOp(cid), writer);
        assertFalse(state.isMember(cid));
    }

    @Test
    void applyAdminRole() {
        AccessListState state = new AccessListState();
        PublicKey writer = ed25519KeyPair.getPublic();
        state.applyOperation(new AddWriterOp(writer), writer);

        Cid128 cid = com.myster.identity.Util.generateCid(rsaKeyPair.getPublic());
        state.applyOperation(new AddMemberOp(cid, Role.ADMIN), writer);
        assertTrue(state.isAdmin(cid));
    }

    @Test
    void nonCanonicalOperationIsSkipped() {
        AccessListState state = new AccessListState();
        PublicKey writer = ed25519KeyPair.getPublic();
        state.applyOperation(new AddWriterOp(writer), writer);

        OpType futureType = OpType.fromString("TELEPORT_USER");
        UnknownOp unknownOp = new UnknownOp(futureType, new byte[]{1, 2, 3});
        state.applyOperation(unknownOp, writer);

        assertEquals(1, state.getWriters().size());
    }

    @Test
    void setPolicyOverridesPrevious() {
        AccessListState state = new AccessListState();
        PublicKey writer = ed25519KeyPair.getPublic();
        state.applyOperation(new AddWriterOp(writer), writer);

        state.applyOperation(new SetPolicyOp(Policy.defaultPermissive()), writer);
        assertTrue(state.getPolicy().isListFilesPublic());

        state.applyOperation(new SetPolicyOp(Policy.defaultRestrictive()), writer);
        assertFalse(state.getPolicy().isListFilesPublic());
    }

    @Test
    void toMysterTypeMatchesDirectComputation() {
        AccessListState state = new AccessListState();
        state.applyOperation(new SetTypePublicKeyOp(rsaKeyPair.getPublic()),
                             ed25519KeyPair.getPublic());
        MysterType fromState = state.toMysterType();
        MysterType direct = new MysterType(rsaKeyPair.getPublic());
        assertEquals(direct, fromState);
    }

    @Test
    void toMysterTypeIsNullWhenNoPublicKey() {
        AccessListState state = new AccessListState();
        assertNull(state.toMysterType());
    }
}

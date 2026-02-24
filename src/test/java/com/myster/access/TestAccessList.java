package com.myster.access;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.myster.identity.Cid128;
import com.myster.type.MysterType;

/**
 * Tests for {@link AccessList} — chain creation, append, validation, and serialization round-trip.
 */
class TestAccessList {
    private static KeyPair ed25519KeyPair;
    private static KeyPair rsaKeyPair;

    @BeforeAll
    static void generateKeys() throws Exception {
        ed25519KeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        rsaKeyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
    }

    private AccessList createTestChain() throws IOException {
        return AccessList.createGenesis(
                rsaKeyPair.getPublic(),
                ed25519KeyPair,
                Collections.emptyList(),
                List.of("onramp1.example.com:6669"),
                Policy.defaultRestrictive(),
                "Test Type",
                "A test type for unit tests",
                new String[]{"mp3", "flac"},
                false);
    }

    @Test
    void genesisBlockHasCorrectHeight() throws IOException {
        AccessList list = createTestChain();
        assertEquals(0, list.getHeight());
        assertEquals(0, list.getGenesisBlock().getHeight());
    }

    @Test
    void mysterTypeMatchesRsaKey() throws IOException {
        AccessList list = createTestChain();
        MysterType expected = new MysterType(rsaKeyPair.getPublic());
        assertEquals(expected, list.getMysterType());
    }

    @Test
    void mysterTypeIs16BytesNotOld32Bytes() throws IOException {
        AccessList list = createTestChain();
        byte[] mysterTypeBytes = list.getMysterType().toBytes();
        assertEquals(16, mysterTypeBytes.length, "MysterType must be 16 bytes (MD5), not 32 (SHA-256)");

        byte[] expected = MysterType.toShortBytes(rsaKeyPair.getPublic());
        assertArrayEquals(expected, mysterTypeBytes);
    }

    @Test
    void genesisStateDerived() throws IOException {
        AccessList list = createTestChain();
        AccessListState state = list.getState();

        assertTrue(state.isWriter(ed25519KeyPair.getPublic()));
        assertArrayEquals(rsaKeyPair.getPublic().getEncoded(),
                          state.getTypePublicKey().getEncoded());
        assertEquals("Test Type", state.getName());
        assertEquals("A test type for unit tests", state.getDescription());
        assertArrayEquals(new String[]{"mp3", "flac"}, state.getExtensions());
        assertFalse(state.isSearchInArchives());
        assertFalse(state.getPolicy().isDiscoverable());
        assertEquals(1, state.getOnramps().size());
        assertEquals("onramp1.example.com:6669", state.getOnramps().get(0));
    }

    @Test
    void appendBlockIncreasesHeight() throws IOException {
        AccessList list = createTestChain();
        list.appendBlock(new SetNameOp("New Name"), ed25519KeyPair);
        assertEquals(1, list.getHeight());
        assertEquals("New Name", list.getState().getName());
    }

    @Test
    void appendMultipleBlocks() throws IOException {
        AccessList list = createTestChain();
        list.appendBlock(new SetNameOp("Updated Name"), ed25519KeyPair);
        list.appendBlock(new SetDescriptionOp("Updated desc"), ed25519KeyPair);
        list.appendBlock(new SetSearchInArchivesOp(true), ed25519KeyPair);
        assertEquals(3, list.getHeight());
        assertEquals("Updated Name", list.getState().getName());
        assertEquals("Updated desc", list.getState().getDescription());
        assertTrue(list.getState().isSearchInArchives());
    }

    @Test
    void appendWithUnauthorizedKeyFails() throws Exception {
        AccessList list = createTestChain();
        KeyPair otherKey = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();

        assertThrows(IllegalStateException.class, () ->
                list.appendBlock(new SetNameOp("Hacked!"), otherKey));
    }

    @Test
    void validatePassesForValidChain() throws IOException {
        AccessList list = createTestChain();
        list.appendBlock(new SetNameOp("Updated"), ed25519KeyPair);
        list.appendBlock(new SetDescriptionOp("More changes"), ed25519KeyPair);
        assertDoesNotThrow(list::validate);
    }

    @Test
    void fullChainSerializationRoundTrip() throws IOException {
        AccessList original = createTestChain();
        original.appendBlock(new SetNameOp("After genesis"), ed25519KeyPair);

        Cid128 cid = com.myster.identity.Util.generateCid(rsaKeyPair.getPublic());
        original.appendBlock(new AddMemberOp(cid, Role.ADMIN), ed25519KeyPair);
        original.appendBlock(new SetPolicyOp(Policy.defaultPermissive()), ed25519KeyPair);

        byte[] bytes = AccessListStorageUtils.toBytes(original);
        AccessList restored = AccessListStorageUtils.fromBytes(bytes);

        assertEquals(original.getMysterType(), restored.getMysterType());
        assertEquals(original.getHeight(), restored.getHeight());
        assertEquals(original.getBlocks().size(), restored.getBlocks().size());

        assertEquals(original.getState().getName(), restored.getState().getName());
        assertEquals(original.getState().getPolicy(), restored.getState().getPolicy());
        assertTrue(restored.getState().isMember(cid));
        assertTrue(restored.getState().isAdmin(cid));
    }

    @Test
    void serializationPreservesBlockHashes() throws IOException {
        AccessList original = createTestChain();
        original.appendBlock(new SetNameOp("Name"), ed25519KeyPair);

        byte[] bytes = AccessListStorageUtils.toBytes(original);
        AccessList restored = AccessListStorageUtils.fromBytes(bytes);

        for (int i = 0; i < original.getBlocks().size(); i++) {
            assertArrayEquals(
                    original.getBlocks().get(i).computeHash(),
                    restored.getBlocks().get(i).computeHash(),
                    "Block " + i + " hash mismatch after round-trip");
        }
    }

    @Test
    void doubleSerializationProducesIdenticalBytes() throws IOException {
        AccessList original = createTestChain();
        original.appendBlock(new AddOnrampOp("server2.example.com"), ed25519KeyPair);

        byte[] bytes1 = AccessListStorageUtils.toBytes(original);
        byte[] bytes2 = AccessListStorageUtils.toBytes(original);
        assertArrayEquals(bytes1, bytes2);
    }

    @Test
    void publicTypeHasPermissivePolicy() throws IOException {
        AccessList list = AccessList.createGenesis(
                rsaKeyPair.getPublic(),
                ed25519KeyPair,
                Collections.emptyList(),
                Collections.emptyList(),
                Policy.defaultPermissive(),
                "Public Type",
                null, null, false);

        assertTrue(list.getState().getPolicy().isDiscoverable());
        assertTrue(list.getState().getPolicy().isListFilesPublic());
    }

    @Test
    void privateTypeHasRestrictivePolicy() throws IOException {
        AccessList list = AccessList.createGenesis(
                rsaKeyPair.getPublic(),
                ed25519KeyPair,
                Collections.emptyList(),
                Collections.emptyList(),
                Policy.defaultRestrictive(),
                "Private Type",
                null, null, false);

        assertFalse(list.getState().getPolicy().isDiscoverable());
        assertFalse(list.getState().getPolicy().isListFilesPublic());
    }

    @Test
    void genesisWithMembersAndOnramps() throws IOException {
        Cid128 cid = com.myster.identity.Util.generateCid(rsaKeyPair.getPublic());

        AccessList list = AccessList.createGenesis(
                rsaKeyPair.getPublic(),
                ed25519KeyPair,
                List.of(new AddMemberOp(cid, Role.MEMBER)),
                List.of("server1.com", "server2.com:6669"),
                Policy.defaultRestrictive(),
                "With Members",
                null, null, false);

        assertTrue(list.getState().isMember(cid));
        assertEquals(2, list.getState().getOnramps().size());
    }

    @Test
    void serializeAndDeserializeChainWithAllOperationTypes() throws IOException {
        Cid128 cid = com.myster.identity.Util.generateCid(rsaKeyPair.getPublic());

        AccessList list = AccessList.createGenesis(
                rsaKeyPair.getPublic(),
                ed25519KeyPair,
                List.of(new AddMemberOp(cid, Role.ADMIN)),
                List.of("onramp.example.com"),
                Policy.defaultRestrictive(),
                "Full Test",
                "Description",
                new String[]{"zip", "tar"},
                true);

        list.appendBlock(new SetNameOp("Updated Name"), ed25519KeyPair);
        list.appendBlock(new SetDescriptionOp("Updated Description"), ed25519KeyPair);
        list.appendBlock(new SetExtensionsOp(new String[]{"gz"}), ed25519KeyPair);
        list.appendBlock(new SetSearchInArchivesOp(false), ed25519KeyPair);
        list.appendBlock(new SetPolicyOp(Policy.defaultPermissive()), ed25519KeyPair);
        list.appendBlock(new AddOnrampOp("onramp2.example.com"), ed25519KeyPair);
        list.appendBlock(new RemoveOnrampOp("onramp.example.com"), ed25519KeyPair);
        list.appendBlock(new RemoveMemberOp(cid), ed25519KeyPair);

        byte[] bytes = AccessListStorageUtils.toBytes(list);
        AccessList restored = AccessListStorageUtils.fromBytes(bytes);

        assertDoesNotThrow(restored::validate);
        assertEquals(list.getHeight(), restored.getHeight());
        assertEquals("Updated Name", restored.getState().getName());
        assertEquals("Updated Description", restored.getState().getDescription());
        assertArrayEquals(new String[]{"gz"}, restored.getState().getExtensions());
        assertFalse(restored.getState().isSearchInArchives());
        assertTrue(restored.getState().getPolicy().isDiscoverable());
        assertEquals(1, restored.getState().getOnramps().size());
        assertEquals("onramp2.example.com", restored.getState().getOnramps().get(0));
        assertFalse(restored.getState().isMember(cid));
    }
}

package com.myster.access;

import java.security.KeyPair;
import java.security.KeyPairGenerator;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link AccessBlock} construction and canonical bytes format.
 */
class TestAccessBlock {
    private static KeyPair ed25519KeyPair;

    @BeforeAll
    static void generateKeys() throws Exception {
        ed25519KeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    @Test
    void canonicalBytesRequires16ByteMysterType() {
        byte[] prevHash = new byte[32];
        AccessBlock block = new AccessBlock(prevHash, 0, 0L,
                ed25519KeyPair.getPublic(), new byte[]{1}, new byte[64]);

        assertThrows(IllegalArgumentException.class,
                () -> block.toCanonicalBytes(new byte[32]),
                "Should reject 32-byte type (old TypeID format)");

        assertDoesNotThrow(
                () -> block.toCanonicalBytes(new byte[16]),
                "Should accept 16-byte type (MysterType shortBytes)");
    }

    @Test
    void prevHashMustBe32Bytes() {
        assertThrows(IllegalArgumentException.class,
                () -> new AccessBlock(new byte[16], 0, 0L,
                        ed25519KeyPair.getPublic(), new byte[]{1}, new byte[64]));
    }

    @Test
    void computeHashIsDeterministic() {
        byte[] prevHash = new byte[32];
        AccessBlock block = new AccessBlock(prevHash, 0, 12345L,
                ed25519KeyPair.getPublic(), new byte[]{1, 2, 3}, new byte[64]);
        assertArrayEquals(block.computeHash(), block.computeHash());
    }
}


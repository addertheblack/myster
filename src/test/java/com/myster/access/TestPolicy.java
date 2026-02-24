package com.myster.access;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Policy} MessagePak serialization and forward/backward compatibility.
 */
class TestPolicy {

    @Test
    void roundTripRestrictive() throws IOException {
        Policy original = Policy.defaultRestrictive();
        byte[] bytes = original.toMessagePakBytes();
        Policy restored = Policy.fromMessagePakBytes(bytes);
        assertEquals(original, restored);
        assertFalse(restored.isListFilesPublic());
    }

    @Test
    void roundTripPermissive() throws IOException {
        Policy original = Policy.defaultPermissive();
        byte[] bytes = original.toMessagePakBytes();
        Policy restored = Policy.fromMessagePakBytes(bytes);
        assertEquals(original, restored);
        assertTrue(restored.isListFilesPublic());
    }

    @Test
    void unknownFieldsAreIgnored() throws IOException {
        // A blob written by a future version with an unknown extra field
        var pak = com.myster.mml.MessagePak.newEmpty();
        pak.putBoolean("/listFilesPublic", true);
        pak.putString("/futureField", "future value");
        byte[] bytes = pak.toBytes();

        Policy restored = Policy.fromMessagePakBytes(bytes);
        assertTrue(restored.isListFilesPublic());
    }

    @Test
    void missingFieldDefaultsToFalse() throws IOException {
        // Empty blob — listFilesPublic should default to false (restrictive)
        var pak = com.myster.mml.MessagePak.newEmpty();
        byte[] bytes = pak.toBytes();

        Policy restored = Policy.fromMessagePakBytes(bytes);
        assertFalse(restored.isListFilesPublic());
    }

    @Test
    void legacyFieldsAreIgnored() throws IOException {
        // A blob written by an older node with the now-removed discoverable and nodeCanJoinPublic keys
        var pak = com.myster.mml.MessagePak.newEmpty();
        pak.putBoolean("/listFilesPublic", true);
        pak.putBoolean("/discoverable", true);
        pak.putBoolean("/nodeCanJoinPublic", true);
        byte[] bytes = pak.toBytes();

        Policy restored = Policy.fromMessagePakBytes(bytes);
        assertTrue(restored.isListFilesPublic());
        // legacy keys silently discarded — no crash, correct value
    }
}

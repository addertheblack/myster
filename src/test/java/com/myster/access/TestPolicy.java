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
        assertFalse(restored.isDiscoverable());
        assertFalse(restored.isListFilesPublic());
        assertFalse(restored.isNodeCanJoinPublic());
    }

    @Test
    void roundTripPermissive() throws IOException {
        Policy original = Policy.defaultPermissive();
        byte[] bytes = original.toMessagePakBytes();
        Policy restored = Policy.fromMessagePakBytes(bytes);
        assertEquals(original, restored);
        assertTrue(restored.isDiscoverable());
        assertTrue(restored.isListFilesPublic());
    }

    @Test
    void roundTripCustom() throws IOException {
        Policy original = new Policy(true, false, true);
        byte[] bytes = original.toMessagePakBytes();
        Policy restored = Policy.fromMessagePakBytes(bytes);
        assertEquals(original, restored);
    }

    @Test
    void unknownFieldsAreIgnored() throws IOException {
        var pak = com.myster.mml.MessagePak.newEmpty();
        pak.putBoolean("/discoverable", true);
        pak.putBoolean("/listFilesPublic", false);
        pak.putBoolean("/nodeCanJoinPublic", false);
        pak.putString("/futureField", "future value");
        byte[] bytes = pak.toBytes();

        Policy restored = Policy.fromMessagePakBytes(bytes);
        assertTrue(restored.isDiscoverable());
        assertFalse(restored.isListFilesPublic());
    }

    @Test
    void missingFieldsDefaultToFalse() throws IOException {
        var pak = com.myster.mml.MessagePak.newEmpty();
        pak.putBoolean("/discoverable", true);
        pak.putBoolean("/listFilesPublic", true);
        byte[] bytes = pak.toBytes();

        Policy restored = Policy.fromMessagePakBytes(bytes);
        assertTrue(restored.isDiscoverable());
        assertTrue(restored.isListFilesPublic());
        assertFalse(restored.isNodeCanJoinPublic());
    }
}


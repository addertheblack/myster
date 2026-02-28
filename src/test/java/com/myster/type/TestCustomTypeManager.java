package com.myster.type;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Map;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the slimmed-down {@link CustomTypeManager} — enabled/disabled index only.
 */
class TestCustomTypeManager {

    private Preferences testPrefs;
    private CustomTypeManager manager;

    @BeforeEach
    void setUp() throws Exception {
        // Use an isolated in-memory-equivalent prefs node for each test
        testPrefs = Preferences.userRoot().node("MysterTest/CustomTypeManager/" + System.nanoTime());
        manager = new CustomTypeManager(testPrefs);
    }

    private MysterType makeType() throws Exception {
        KeyPair kp = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        return new MysterType(kp.getPublic());
    }

    @Test
    void saveAndLoadEnabled() throws Exception {
        MysterType type = makeType();
        manager.saveEnabled(type, true);

        Map<MysterType, Boolean> loaded = manager.loadEnabledTypes();
        assertEquals(1, loaded.size());
        assertTrue(loaded.get(type));
    }

    @Test
    void saveAndLoadDisabled() throws Exception {
        MysterType type = makeType();
        manager.saveEnabled(type, false);

        Map<MysterType, Boolean> loaded = manager.loadEnabledTypes();
        assertEquals(1, loaded.size());
        assertFalse(loaded.get(type));
    }

    @Test
    void multipleTypesRoundTrip() throws Exception {
        MysterType t1 = makeType();
        MysterType t2 = makeType();
        MysterType t3 = makeType();

        manager.saveEnabled(t1, true);
        manager.saveEnabled(t2, false);
        manager.saveEnabled(t3, true);

        Map<MysterType, Boolean> loaded = manager.loadEnabledTypes();
        assertEquals(3, loaded.size());
        assertTrue(loaded.get(t1));
        assertFalse(loaded.get(t2));
        assertTrue(loaded.get(t3));
    }

    @Test
    void deleteRemovesNode() throws Exception {
        MysterType type = makeType();
        manager.saveEnabled(type, true);

        manager.deleteCustomType(type);

        Map<MysterType, Boolean> loaded = manager.loadEnabledTypes();
        assertTrue(loaded.isEmpty());
    }

    @Test
    void deleteNonExistentTypeIsNoop() throws Exception {
        MysterType type = makeType();
        // Never saved — delete should not throw
        assertDoesNotThrow(() -> manager.deleteCustomType(type));
    }

    @Test
    void legacyMetadataKeysAreIgnored() throws Exception {
        MysterType type = makeType();

        // Write a node that looks like the old CustomTypeManager format
        Preferences legacyNode = testPrefs.node("CustomTypes").node(type.toHexString());
        legacyNode.put("name", "Legacy Name");
        legacyNode.put("description", "Legacy Description");
        legacyNode.put("publicKey", "not-a-real-key");
        legacyNode.putBoolean("enabled", true);
        legacyNode.put("extensions", "mp3,flac");
        legacyNode.flush();

        // Should load without crashing and return only the enabled flag
        Map<MysterType, Boolean> loaded = manager.loadEnabledTypes();
        assertEquals(1, loaded.size());
        assertTrue(loaded.get(type), "Should read enabled=true from legacy node");
    }

    @Test
    void malformedNodeNameIsSkipped() throws Exception {
        // Write a node with a non-hex name (simulates corruption or manual editing)
        Preferences badNode = testPrefs.node("CustomTypes").node("not-a-valid-hex-string!");
        badNode.putBoolean("enabled", true);
        badNode.flush();

        // Should not throw, and should return an empty map (malformed node skipped)
        Map<MysterType, Boolean> loaded = manager.loadEnabledTypes();
        assertTrue(loaded.isEmpty(), "Malformed node name should be silently skipped");
    }

    @Test
    void missingEnabledKeyDefaultsToFalse() throws Exception {
        MysterType type = makeType();

        // Write a node but omit the "enabled" key
        Preferences typeNode = testPrefs.node("CustomTypes").node(type.toHexString());
        typeNode.put("someOtherKey", "value");
        typeNode.flush();

        Map<MysterType, Boolean> loaded = manager.loadEnabledTypes();
        assertEquals(1, loaded.size());
        assertFalse(loaded.get(type), "Missing enabled key should default to false");
    }
}


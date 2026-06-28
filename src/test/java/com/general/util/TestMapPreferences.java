package com.general.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("myster")
public class TestMapPreferences {
    @Test
    public void nestedNodePathTraversesAllSegments() throws BackingStoreException {
        Preferences root = new MapPreferences();

        root.node("a/b/c").put("key", "value");

        assertEquals("value", root.node("a").node("b").node("c").get("key", null));
        assertEquals("value", root.node("a/b/c").get("key", null));
        assertEquals("/a/b/c", root.node("a/b/c").absolutePath());
        assertTrue(root.nodeExists("a/b/c"));
    }

    @Test
    public void absoluteNodePathStartsAtRoot() {
        Preferences root = new MapPreferences();
        Preferences child = root.node("a");

        root.node("/a/b").put("key", "value");

        assertEquals("value", child.node("b").get("key", null));
        assertEquals(root, child.node("/"));
    }

    @Test
    public void nodeExistsDoesNotCreateMissingNodes() throws BackingStoreException {
        Preferences root = new MapPreferences();

        assertFalse(root.nodeExists("missing/path"));
        assertFalse(root.nodeExists("missing"));
        assertEquals(0, root.childrenNames().length);
    }

    @Test
    public void removeNodeRemovesChildFromParent() throws BackingStoreException {
        Preferences root = new MapPreferences();
        Preferences child = root.node("a/b");

        child.removeNode();

        assertFalse(root.nodeExists("a/b"));
        assertEquals(0, root.node("a").childrenNames().length);
    }
}

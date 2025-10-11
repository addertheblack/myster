package com.myster.mml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TestMessagePackSerializer {
    private MessagePackSerializer serializer;

    @BeforeEach
    void setUp() {
        serializer = new MessagePackSerializer();
    }

    @Test
    void testPathExceptions() {
        assertThrows(NonExistantPathException.class, () -> {
            serializer.navigateToParent(null);
        });
        
        assertThrows(DoubleSlashException.class, () -> {
            serializer.navigateToParent("//invalid/path/");
        });
        
        assertThrows(NonExistantPathException.class, () -> {
            serializer.navigateToParent("/nonexistent/path/");
        });
    }

    @Test
    void testLeafAsBranchExceptions() {
        serializer.put("/path/to/value", "test");
        
        assertThrows(LeafAsABranchException.class, () -> {
            serializer.navigateToParent("/path/to/value/deeper/");
        });
        
        assertThrows(LeafAsABranchException.class, () -> {
            serializer.list("/path/to/value/");
        });
    }

    @Test
    void testBranchAsLeafExceptions() {
        serializer.put("/path/to/branch/value", "test");
        
        assertThrows(BranchAsALeafException.class, () -> {
            serializer.get("/path/to/branch");
        });
    }

    @Test
    void putAndGetSimpleValue() {
        serializer.put("/test", "value");
        assertEquals("value", serializer.get("/test").orElse(null));
    }

    @Test
    void putAndGetNestedValues() {
        serializer.put("/parent/child", "value");
        assertEquals("value", serializer.get("/parent/child").orElse(null));
    }

    @Test
    void listDirectoryContents() {
        serializer.put("/dir/file1", "value1");
        serializer.put("/dir/file2", "value2");
        serializer.put("/dir/subdir/file3", "value3");

        List<String> contents = serializer.list("/dir/");
        assertEquals(3, contents.size());
        assertTrue(contents.contains("file1"));
        assertTrue(contents.contains("file2"));
        assertTrue(contents.contains("subdir"));
    }

    @Test
    void testPathParseCommonErrors() {
        assertThrows(MMLPathException.class, () -> serializer.get(""));
        assertThrows(MMLPathException.class, () -> serializer.list(""));
        assertThrows(NullPointerException.class, () -> serializer.get(null));
        assertThrows(NullPointerException.class, () -> serializer.list(null));
        assertThrows(NoStartingSlashException.class, () -> serializer.get("asdfasdf/"));
        assertThrows(NoStartingSlashException.class, () -> serializer.list("asdfasdf/"));
        assertThrows(DoubleSlashException.class, () -> serializer.get("/asdfasdf//asdfas"));
        assertThrows(DoubleSlashException.class, () -> serializer.list("/asdfasdf//asdfas"));
    }

    @Test
    void isAValue() {
        serializer.put("/test", "value");
        serializer.put("/dir/file", "content");

        assertTrue(serializer.isAValue("/test"));
        assertTrue(serializer.isAValue("/dir/file"));
        assertFalse(serializer.isAValue("/dir"));
        assertFalse(serializer.isAValue("/nonexistent"));
    }

    @Test
    void isADirectory() {
        serializer.put("/dir/file", "content");

        assertTrue(serializer.isADirectory("/"));
        assertTrue(serializer.isADirectory("/dir/"));
        assertFalse(serializer.isADirectory("/dir/file"));
        assertFalse(serializer.isADirectory("/nonexistent/"));
    }

    @Test
    void serializeAndDeserialize() throws IOException {
        serializer.put("/test", "value");
        serializer.put("/dir/file", "content");
        
        byte[] data = serializer.toBytes();
        MessagePackSerializer deserialized = new MessagePackSerializer(data);
        
        assertEquals("value", deserialized.get("/test").orElse(null));
        assertEquals("content", deserialized.get("/dir/file").orElse(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/test/path",
        "/very/deep/nested/path/here",
        "/path/with/multiple/values",
        "/single"
    })
    void testVariousPathDepths(String path) {
        serializer.put(path, "test-value");
        assertEquals("test-value", serializer.get(path).orElse(null));
    }

    @Test
    void testCorruptDataHandling() {
        // Try to deserialize corrupt data
        assertThrows(IOException.class, () -> {
            new MessagePackSerializer(new byte[]{1, 2, 3, 4});
        });
    }

    // New tests for integer types
    @Test
    void testIntegerTypes() {
        // Test putting and getting different integer types
        serializer.putShort("/short", (short) 100);
        serializer.putInt("/int", 50000);
        serializer.putLong("/long", 3000000000L);

        // All integers are stored as Long internally
        assertEquals((short) 100, serializer.getShort("/short").orElse(null));
        assertEquals(50000, serializer.getInt("/int").orElse(null));
        assertEquals(3000000000L, serializer.getLong("/long").orElse(null));

        // Test cross-type access where values fit
        assertEquals(100L, serializer.getLong("/short").orElse(null));
        assertEquals(100, serializer.getInt("/short").orElse(null));
        assertEquals(50000L, serializer.getLong("/int").orElse(null));

        // Test range validation failures
        assertThrows(ClassCastException.class, () -> {
            serializer.getShort("/int"); // 50000 doesn't fit in short
        });
        
        assertThrows(ClassCastException.class, () -> {
            serializer.getInt("/long"); // 3000000000L doesn't fit in int
        });
    }

    @Test
    void testTypeMismatchExceptions() {
        serializer.put("/string", "hello");
        serializer.putInt("/int", 42);
        serializer.putBoolean("/bool", true);

        // Test ClassCastException when types don't match
        assertThrows(ClassCastException.class, () -> {
            serializer.getInt("/string");
        });
        
        assertThrows(ClassCastException.class, () -> {
            serializer.get("/int"); // get() is for strings, /int contains a Long
        });
        
        assertThrows(ClassCastException.class, () -> {
            serializer.getBoolean("/string");
        });
    }

    @Test
    void testArrayTypes() {
        byte[] byteArray = {1, 2, 3, 4, 5};
        int[] intArray = {10, 20, 30};
        short[] shortArray = {100, 200, 300};
        long[] longArray = { 1000L, 2000L, 3000L };
        Object[] objectArray = { "hello", 42L, true, byteArray };

        serializer.putByteArray("/bytes", byteArray);
        serializer.putIntArray("/ints", intArray);
        serializer.putShortArray("/shorts", shortArray);
        serializer.putLongArray("/longs", longArray);
        serializer.putObjectArray("/objects", objectArray);

        // Test retrieval
        assertArrayEquals(byteArray, serializer.getByteArray("/bytes").orElse(null));
        assertArrayEquals(intArray, serializer.getIntArray("/ints").orElse(null));
        assertArrayEquals(shortArray, serializer.getShortArray("/shorts").orElse(null));
        assertArrayEquals(longArray, serializer.getLongArray("/longs").orElse(null));
        
        Object[] retrievedObjects = serializer.getObjectArray("/objects").orElse(null);
        assertEquals(4, retrievedObjects.length);
        assertEquals("hello", retrievedObjects[0]);
        assertEquals(42L, retrievedObjects[1]);
        assertEquals(true, retrievedObjects[2]);
        assertArrayEquals(byteArray, (byte[]) retrievedObjects[3]);
    }

    @Test
    void testSerializationOfNewTypes() throws IOException {
        serializer.putShort("/short", (short) 500);
        serializer.putInt("/int", 100000);
        serializer.putLong("/long", 5000000000L);
        serializer.putByteArray("/bytes", new byte[]{10, 20, 30});
        serializer.putIntArray("/ints", new int[]{1, 2, 3});

        byte[] data = serializer.toBytes();
        MessagePackSerializer deserialized = new MessagePackSerializer(data);

        assertEquals((short) 500, deserialized.getShort("/short").orElse(null));
        assertEquals(100000, deserialized.getInt("/int").orElse(null));
        assertEquals(5000000000L, deserialized.getLong("/long").orElse(null));
        assertArrayEquals(new byte[]{10, 20, 30}, deserialized.getByteArray("/bytes").orElse(null));
        assertArrayEquals(new int[]{1, 2, 3}, deserialized.getIntArray("/ints").orElse(null));
    }

    @Test
    void testRemove() {
        // Set up test data
        serializer.put("/file1", "content1");
        serializer.put("/dir/file2", "content2");
        serializer.put("/dir/subdir/file3", "content3");
        serializer.putInt("/number", 42);

        // Test successful removal of leaf values
        assertTrue(serializer.remove("/file1"));
        assertFalse(serializer.get("/file1").isPresent());

        assertTrue(serializer.remove("/dir/file2"));
        assertFalse(serializer.get("/dir/file2").isPresent());

        assertTrue(serializer.remove("/number"));
        assertFalse(serializer.getInt("/number").isPresent());

        // Verify other paths still exist
        assertEquals("content3", serializer.get("/dir/subdir/file3").orElse(null));
        assertTrue(serializer.isADirectory("/dir/"));
        assertTrue(serializer.isADirectory("/dir/subdir/"));

        // Test removing non-existent keys
        assertFalse(serializer.remove("/nonexistent"));
        assertFalse(serializer.remove("/dir/nonexistent"));

        // Test that remove() cannot remove directories
        assertFalse(serializer.remove("/dir"));
        assertFalse(serializer.remove("/dir/subdir"));
        assertTrue(serializer.isADirectory("/dir/"));
        assertTrue(serializer.isADirectory("/dir/subdir/"));

        // Test null path
        assertFalse(serializer.remove(null));

        // Test invalid paths
        assertThrows(MMLPathException.class, () -> {
            serializer.remove("/dir/"); // Branch path not allowed for remove()
        });
    }

    @Test
    void testRemoveDir() {
        // Set up test data
        serializer.put("/file1", "content1");
        serializer.put("/dir/file2", "content2");
        serializer.put("/dir/subdir/file3", "content3");
        serializer.put("/dir/subdir/file4", "content4");
        serializer.put("/other/file5", "content5");

        // Test removing a directory with branch path syntax
        assertTrue(serializer.removeDir("/dir/subdir/"));
        assertFalse(serializer.isADirectory("/dir/subdir/"));
        assertFalse(serializer.get("/dir/subdir/file3").isPresent());
        assertFalse(serializer.get("/dir/subdir/file4").isPresent());

        // Verify parent directory still exists
        assertTrue(serializer.isADirectory("/dir/"));
        assertEquals("content2", serializer.get("/dir/file2").orElse(null));

        // Test removing a directory with branch path syntax only
        assertTrue(serializer.removeDir("/dir/"));
        assertFalse(serializer.isADirectory("/dir/"));
        assertFalse(serializer.get("/dir/file2").isPresent());

        // Verify other paths still exist
        assertEquals("content1", serializer.get("/file1").orElse(null));
        assertEquals("content5", serializer.get("/other/file5").orElse(null));

        // Test removing non-existent directory
        assertFalse(serializer.removeDir("/nonexistent/"));

        // Test that root cannot be removed
        assertFalse(serializer.removeDir("/"));

        // Test null path
        assertFalse(serializer.removeDir(null));

        // Test that leaf paths are rejected
        assertThrows(MMLPathException.class, () -> {
            serializer.removeDir("/other"); // Leaf path not allowed for removeDir()
        });
    }

    @Test
    void testRemoveWithSerialization() throws IOException {
        // Set up initial data
        serializer.put("/keep/file1", "keep1");
        serializer.put("/remove/file2", "remove2");
        serializer.put("/removedir/file3", "remove3");
        serializer.put("/removedir/subdir/file4", "remove4");

        // Remove some data
        assertTrue(serializer.remove("/remove/file2"));
        assertTrue(serializer.removeDir("/removedir/"));

        // Serialize and deserialize
        byte[] data = serializer.toBytes();
        MessagePackSerializer deserialized = new MessagePackSerializer(data);

        // Verify removed data is gone
        assertFalse(deserialized.get("/remove/file2").isPresent());
        assertFalse(deserialized.isADirectory("/removedir/"));
        assertFalse(deserialized.get("/removedir/file3").isPresent());
        assertFalse(deserialized.get("/removedir/subdir/file4").isPresent());

        // Verify kept data is still there
        assertEquals("keep1", deserialized.get("/keep/file1").orElse(null));
        assertTrue(deserialized.isADirectory("/keep/"));

        // The /remove/ directory should still exist but be empty
        assertTrue(deserialized.isADirectory("/remove/"));
        List<String> removeContents = deserialized.list("/remove/");
        assertEquals(0, removeContents.size());
    }

    @Test
    void testRemoveEdgeCases() {
        // Set up nested structure
        serializer.put("/a/b/c/d/file", "deep");

        // Remove deep file
        assertTrue(serializer.remove("/a/b/c/d/file"));
        assertFalse(serializer.get("/a/b/c/d/file").isPresent());

        // All parent directories should still exist
        assertTrue(serializer.isADirectory("/a/"));
        assertTrue(serializer.isADirectory("/a/b/"));
        assertTrue(serializer.isADirectory("/a/b/c/"));
        assertTrue(serializer.isADirectory("/a/b/c/d/"));

        // Add multiple files in a directory
        serializer.put("/multi/file1", "one");
        serializer.put("/multi/file2", "two");
        serializer.put("/multi/file3", "three");

        // Remove one file
        assertTrue(serializer.remove("/multi/file2"));
        assertEquals("one", serializer.get("/multi/file1").orElse(null));
        assertFalse(serializer.get("/multi/file2").isPresent());
        assertEquals("three", serializer.get("/multi/file3").orElse(null));

        // Remove entire directory
        assertTrue(serializer.removeDir("/multi/"));
        assertFalse(serializer.isADirectory("/multi/"));
        assertFalse(serializer.get("/multi/file1").isPresent());
        assertFalse(serializer.get("/multi/file3").isPresent());
    }

    private void assertArrayEquals(byte[] expected, byte[] actual) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i]);
        }
    }

    private void assertArrayEquals(int[] expected, int[] actual) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i]);
        }
    }

    private void assertArrayEquals(short[] expected, short[] actual) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i]);
        }
    }

    private void assertArrayEquals(long[] expected, long[] actual) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i]);
        }
    }
}
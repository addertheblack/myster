package com.myster.mml;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

class TestRobustMessagePackSerializer {
    private static final Logger LOGGER = Logger.getLogger(TestRobustMessagePackSerializer.class.getName());
    private RobustMessagePackSerializer serializer;

    @BeforeEach
    void setUp() {
        serializer = new RobustMessagePackSerializer();
    }

    @Test
    @DisplayName("Test constructor with corrupt data")
    void testConstructorWithCorruptData() {
        byte[] corruptData = new byte[]{1, 2, 3, 4};
        // Should throw IOException since constructor can't handle corrupt data
        assertThrows(IOException.class, () -> {
            new RobustMessagePackSerializer(corruptData);
        });
    }

    @Test
    @DisplayName("Test trace functionality")
    void testTrace() {
        assertFalse(serializer.isTrace());
        serializer.setTrace(true);
        assertTrue(serializer.isTrace());
    }

    @Test
    @DisplayName("Test data obliteration behavior")
    void testDataObliteration() {
        // Set up initial nested structure
        serializer.put("/foo/bar/baz", "nested value");
        assertEquals("nested value", serializer.get("/foo/bar/baz").orElse(null));
        
        // Now obliterate it by putting a value at parent path
        serializer.put("/foo", "parent value");
        
        // The nested structure should be gone
        assertTrue(serializer.get("/foo/bar/baz").isEmpty());
        assertEquals("parent value", serializer.get("/foo").orElse(null));
        
        // Reverse test - put nested value after parent value
        serializer.put("/test", "simple value");
        assertEquals("simple value", serializer.get("/test").orElse(null));
        
        // Put nested structure - should obliterate the simple value
        serializer.put("/test/nested/deep", "deep value");
        assertTrue(serializer.get("/test").isEmpty()); // Original value gone
        assertEquals("deep value", serializer.get("/test/nested/deep").orElse(null));
    }

    @Test
    @DisplayName("Test ClassCastException handling - data type mismatches return empty")
    void testClassCastExceptionHandling() {
        // Put a string, try to get as different types
        serializer.put("/string", "hello");
        assertTrue(serializer.getInt("/string").isEmpty());
        assertTrue(serializer.getLong("/string").isEmpty());
        assertTrue(serializer.getBoolean("/string").isEmpty());
        assertTrue(serializer.getDate("/string").isEmpty());
        assertTrue(serializer.getIntArray("/string").isEmpty());
        
        // Put an int, try to get as string
        serializer.putInt("/number", 42);
        assertTrue(serializer.get("/number").isEmpty()); // String getter on Long value
        assertEquals(42, serializer.getInt("/number").orElse(0)); // Should work
        
        // Put array, try to get as scalar
        serializer.putIntArray("/array", new int[]{1, 2, 3});
        assertTrue(serializer.getInt("/array").isEmpty());
        assertTrue(serializer.get("/array").isEmpty());
        assertArrayEquals(new int[]{1, 2, 3}, serializer.getIntArray("/array").orElse(null));
    }

    @Test
    @DisplayName("Test programmer error exceptions are still thrown")
    void testProgrammerErrorExceptions() {
        // These should throw exceptions (programmer errors)
        assertThrows(DoubleSlashException.class, () -> {
            serializer.get("//bad/path");
        });
        
        assertThrows(NoStartingSlashException.class, () -> {
            serializer.get("no/leading/slash");
        });
        
        assertThrows(NullPointerException.class, () -> {
            serializer.get(null);
        });
        
        assertThrows(MMLPathException.class, () -> {
            serializer.get("");
        });
    }

    @Test
    @DisplayName("Test branch/leaf mismatch handling")
    void testBranchLeafMismatchHandling() {
        // Create nested structure
        serializer.put("/path/to/value", "test");
        
        // Try to get intermediate path as value - should return empty, not throw
        assertTrue(serializer.get("/path").isEmpty());
        assertTrue(serializer.get("/path/to").isEmpty());
        
        // Try to list a leaf - should return empty, not throw
        assertTrue(serializer.list("/path/to/value").isEmpty());
        
        // Original value should remain intact
        assertEquals("test", serializer.get("/path/to/value").orElse(null));
    }

    @Test
    @DisplayName("Test all data types work correctly")
    void testAllDataTypes() {
        // Test all primitive types
        serializer.putBoolean("/bool", true);
        serializer.putInt("/int", 42);
        serializer.putLong("/long", 123456789L);
        serializer.putShort("/short", (short) 100);
        serializer.putFloat("/float", 3.14f);
        serializer.putDouble("/double", 2.718);
        serializer.putDate("/date", new Date());
        serializer.putByteArray("/bytes", new byte[]{1, 2, 3});
        serializer.putIntArray("/intArray", new int[]{10, 20, 30});
        serializer.putLongArray("/longArray", new long[]{100L, 200L, 300L});
        serializer.putShortArray("/shortArray", new short[]{1, 2, 3});
        serializer.putDoubleArray("/doubleArray", new double[]{1.1, 2.2, 3.3});
        serializer.putObjectArray("/objArray", new String[]{"a", "b", "c"});
        
        // Verify they can be retrieved
        assertTrue(serializer.getBoolean("/bool").orElse(false));
        assertEquals(42, serializer.getInt("/int").orElse(0));
        assertEquals(123456789L, serializer.getLong("/long").orElse(0L));
        assertEquals((short)100, serializer.getShort("/short").orElse((short) 0));
        assertEquals(3.14f, serializer.getFloat("/float").orElse(0f), 0.01f);
        assertEquals(2.718, serializer.getDouble("/double").orElse(0.0), 0.001);
        assertNotNull(serializer.getDate("/date").orElse(null));
        assertArrayEquals(new byte[]{1, 2, 3}, serializer.getByteArray("/bytes").orElse(null));
        assertArrayEquals(new int[]{10, 20, 30}, serializer.getIntArray("/intArray").orElse(null));
        assertArrayEquals(new long[]{100L, 200L, 300L}, serializer.getLongArray("/longArray").orElse(null));
        assertArrayEquals(new short[]{1, 2, 3}, serializer.getShortArray("/shortArray").orElse(null));
        assertArrayEquals(new double[]{1.1, 2.2, 3.3}, serializer.getDoubleArray("/doubleArray").orElse(null), 0.001);
        assertArrayEquals(new String[]{"a", "b", "c"}, serializer.getObjectArray("/objArray").orElse(null));
    }

    @Test
    @DisplayName("Test directory operations")
    void testDirectoryOperations() {
        // Create structure
        serializer.put("/dir1/file1", "value1");
        serializer.put("/dir1/file2", "value2");
        serializer.put("/dir2/file3", "value3");
        
        // Test listing
        List<String> rootList = serializer.list("/");
        assertTrue(rootList.isEmpty()); // Root only contains directories, not values
        
        List<String> dir1List = serializer.list("/dir1/");
        assertEquals(2, dir1List.size());
        assertTrue(dir1List.contains("file1"));
        assertTrue(dir1List.contains("file2"));
        
        // Test isAValue and isADirectory
        assertTrue(serializer.isAValue("/dir1/file1"));
        assertFalse(serializer.isAValue("/dir1/"));
        assertTrue(serializer.isADirectory("/dir1/"));
        assertFalse(serializer.isADirectory("/dir1/file1"));
        
        // Test removal
        assertTrue(serializer.remove("/dir1/file1"));
        assertTrue(serializer.get("/dir1/file1").isEmpty());
        assertEquals(1, serializer.list("/dir1/").size());
        
        assertTrue(serializer.removeDir("/dir2/"));
        assertTrue(serializer.get("/dir2/file3").isEmpty());
    }

    @Test
    @DisplayName("Test serialization and deserialization")
    void testSerialization() throws IOException {
        // Create test data
        serializer.put("/test1", "value1");
        serializer.putInt("/test2", 42);
        serializer.putIntArray("/test3", new int[]{1, 2, 3});
        
        // Serialize
        byte[] data = serializer.toBytes();
        assertNotNull(data);
        assertTrue(data.length > 0);
        
        // Deserialize into new instance
        RobustMessagePackSerializer newSerializer = new RobustMessagePackSerializer(data);
        
        // Verify data is intact
        assertEquals("value1", newSerializer.get("/test1").orElse(null));
        assertEquals(42, newSerializer.getInt("/test2").orElse(0));
        assertArrayEquals(new int[]{1, 2, 3}, newSerializer.getIntArray("/test3").orElse(null));
    }

    @Test
    @DisplayName("Test null handling")
    void testNullHandling() {
        // Null values should be handled gracefully
        serializer.put("/null", null);
        assertTrue(serializer.get("/null").isEmpty());
        
        serializer.putIntArray("/nullArray", null);
        assertTrue(serializer.getIntArray("/nullArray").isEmpty());
        
        serializer.putObjectArray("/nullObjArray", null);
        assertTrue(serializer.getObjectArray("/nullObjArray").isEmpty());
    }

    @Test
    @DisplayName("Test clearAPath functionality")
    void testClearAPath() {
        // Create a directory structure
        serializer.put("/existing/path/value", "original");
        assertTrue(serializer.isADirectory("/existing/"));
        assertTrue(serializer.isADirectory("/existing/path/"));
        
        // Put a value where a directory exists - should clear the path
        serializer.putInt("/existing", 42);
        
        // Directory should be gone, value should be there
        assertFalse(serializer.isADirectory("/existing/"));
        assertTrue(serializer.isAValue("/existing"));
        assertEquals(42, serializer.getInt("/existing").orElse(0));
        
        // Original nested value should be gone
        assertTrue(serializer.get("/existing/path/value").isEmpty());
    }

    @Test
    @DisplayName("Test edge cases and boundary conditions")
    void testEdgeCases() {
        // Test root path operations
        assertTrue(serializer.isADirectory("/"));
        assertTrue(serializer.list("/").isEmpty()); // Empty root
        
        // Test very deep nesting
        String deepPath = "/a/b/c/d/e/f/g/h/i/j/k/l/m/n/o/p";
        serializer.put(deepPath, "deep");
        assertEquals("deep", serializer.get(deepPath).orElse(null));
        
        // Test special characters in values (paths are validated separately)
        serializer.put("/special", "value with spaces and symbols !@#$%^&*()");
        assertEquals("value with spaces and symbols !@#$%^&*()", serializer.get("/special").orElse(null));
        
        // Test empty arrays
        serializer.putIntArray("/emptyArray", new int[0]);
        assertArrayEquals(new int[0], serializer.getIntArray("/emptyArray").orElse(null));
    }
}
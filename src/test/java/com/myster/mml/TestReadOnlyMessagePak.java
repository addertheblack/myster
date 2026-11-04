package com.myster.mml;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TestReadOnlyMessagePak {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void readsTypedValuesAndPreservesDisplayAndSerialization(boolean decoded) throws Exception {
        MessagePak pack = MessagePak.newEmpty();
        pack.putString("/text", "hello");
        pack.putBoolean("/boolean", true);
        pack.putInt("/int", 17);
        pack.putShort("/short", (short) 23);
        pack.putLong("/long", 4_294_967_296L);
        pack.putFloat("/float", 1.5f);
        pack.putDouble("/double", 2.5);
        pack.putByteArray("/bytes", (byte) 1, (byte) 2);
        pack.putIntArray("/ints", 1, 2);
        pack.putLongArray("/longs", 3, 4);
        pack.putShortArray("/shorts", (short) 5, (short) 6);
        pack.putDoubleArray("/doubles", 7.5, 8.5);
        pack.putStringArray("/strings", "a", null, "b");
        pack.putObjectArray("/objects", new Object[] {"hello", 9L, null});
        pack.putLong("/nested/value", 10);
        if (decoded) {
            pack = MessagePak.fromBytes(pack.toBytes());
        }

        ReadOnlyMessagePak view = pack.asReadOnly();
        assertFalse(view instanceof MessagePak);
        assertEquals("hello", view.getString("/text").orElseThrow());
        assertTrue(view.getBoolean("/boolean").orElseThrow());
        assertEquals(17, view.getInt("/int").orElseThrow());
        assertEquals((short) 23, view.getShort("/short").orElseThrow());
        assertEquals(23L, view.getLong("/short").orElseThrow());
        assertEquals(17L, view.getLong("/int").orElseThrow());
        assertEquals(4_294_967_296L, view.getLong("/long").orElseThrow());
        assertEquals(1.5f, view.getFloat("/float").orElseThrow());
        assertEquals(2.5, view.getDouble("/double").orElseThrow());
        assertEquals(4_294_967_296L, view.getValue("/long").orElseThrow());
        assertArrayEquals(new byte[] {1, 2}, view.getByteArray("/bytes").orElseThrow());
        assertArrayEquals(new int[] {1, 2}, view.getIntArray("/ints").orElseThrow());
        assertArrayEquals(new long[] {3, 4}, view.getLongArray("/longs").orElseThrow());
        assertArrayEquals(new short[] {5, 6}, view.getShortArray("/shorts").orElseThrow());
        assertArrayEquals(new double[] {7.5, 8.5}, view.getDoubleArray("/doubles").orElseThrow());
        assertArrayEquals(new String[] {"a", null, "b"}, view.getStringArray("/strings").orElseThrow());
        assertArrayEquals(new Object[] {"hello", 9L, null}, view.getObjectArray("/objects").orElseThrow());
        assertTrue(view.isADirectory("/nested/"));
        assertTrue(view.isAValue("/nested/value"));
        assertEquals(List.of("value"), view.list("/nested/"));
        assertArrayEquals(pack.toBytes(), view.toBytes());
        for (String key : List.of("/text", "/boolean", "/long", "/bytes", "/ints")) {
            assertEquals(pack.get(key), view.get(key));
        }
        // The original display converter rejects null array elements; the view preserves it.
        MessagePak source = pack;
        assertThrows(NullPointerException.class, () -> source.get("/objects"));
        assertThrows(NullPointerException.class, () -> view.get("/objects"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void detachesArraysAndGenericNestedValues(boolean decoded) throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("bytes", new byte[] {1});
        map.put("nested", new Object[] {new Date(123), new long[] {2}, null});
        MessagePak pack = MessagePak.newEmpty();
        pack.putByteArray("/bytes", (byte) 3);
        pack.putIntArray("/ints", 4);
        pack.putLongArray("/longs", 5);
        pack.putShortArray("/shorts", (short) 6);
        pack.putDoubleArray("/doubles", 7);
        pack.putStringArray("/strings", "original");
        pack.putObjectArray("/objects", new Object[] {map, new int[] {8},
                new short[] {9}, new double[] {10}, new String[] {"original"}});
        if (decoded) {
            pack = MessagePak.fromBytes(pack.toBytes());
        }
        byte[] before = pack.toBytes();
        ReadOnlyMessagePak view = pack.asReadOnly();

        view.getByteArray("/bytes").orElseThrow()[0] = 99;
        view.getIntArray("/ints").orElseThrow()[0] = 99;
        view.getLongArray("/longs").orElseThrow()[0] = 99;
        view.getShortArray("/shorts").orElseThrow()[0] = 99;
        view.getDoubleArray("/doubles").orElseThrow()[0] = 99;
        view.getStringArray("/strings").orElseThrow()[0] = "changed";
        mutateNestedValues(view.getObjectArray("/objects").orElseThrow());
        mutateNestedValues((Object[]) view.getValue("/objects").orElseThrow());
        ((byte[]) view.getValue("/bytes").orElseThrow())[0] = 99;
        view.toBytes()[0] = 0;
        assertThrows(UnsupportedOperationException.class, () -> view.list("/").clear());

        assertArrayEquals(before, pack.toBytes());
    }

    @SuppressWarnings("unchecked")
    private static void mutateNestedValues(Object[] values) {
        Map<String, Object> map = (Map<String, Object>) values[0];
        ((byte[]) map.get("bytes"))[0] = 99;
        Object[] nested = (Object[]) map.get("nested");
        if (nested[0] instanceof Date date) {
            date.setTime(99);
        }
        if (nested[1] instanceof long[] longs) {
            longs[0] = 99;
        } else {
            ((Object[]) nested[1])[0] = 99L;
        }
        map.put("added", "changed");
        for (int i = 1; i < values.length; i++) {
            switch (values[i]) {
                case int[] array -> array[0] = 99;
                case short[] array -> array[0] = 99;
                case double[] array -> array[0] = 99;
                case Object[] array -> array[0] = null;
                default -> fail("Unexpected fixture type");
            }
        }
        values[0] = null;
    }

    @Test
    void copiesDatesWithoutChangingOriginalApiOrDateEncoding() throws Exception {
        MessagePak pack = MessagePak.newEmpty();
        Date original = new Date(123);
        byte[] bytes = {1};
        Object[] objects = {"original"};
        pack.putDate("/date", original);
        pack.putByteArray("/bytes", bytes);
        pack.putObjectArray("/objects", objects);
        ReadOnlyMessagePak view = pack.asReadOnly();

        assertSame(original, pack.getDate("/date").orElseThrow());
        assertSame(bytes, pack.getByteArray("/bytes").orElseThrow());
        assertSame(objects, pack.getObjectArray("/objects").orElseThrow());
        assertEquals(pack.get("/date"), view.get("/date"));
        view.getDate("/date").orElseThrow().setTime(99);
        ((Date) view.getValue("/date").orElseThrow()).setTime(99);
        assertEquals(123, original.getTime());

        pack.putDate("/date", new Date(456));
        assertEquals(456, view.getDate("/date").orElseThrow().getTime());
        MessagePak decoded = MessagePak.fromBytes(pack.toBytes());
        assertTrue(decoded.asReadOnly().getDate("/date").isEmpty());
        assertEquals(456L, decoded.getLong("/date").orElseThrow());
    }

    @Test
    void preservesStrictAndRobustReaderErrorsAndMissingValues() throws Exception {
        MessagePak strict = MessagePak.newEmpty();
        strict.putString("/number", "123");
        strict.putLong("/text", 123);
        strict.putLong("/branch/leaf", 1);
        strict.putLong("/tooBig", Long.MAX_VALUE);
        ReadOnlyMessagePak strictView = strict.asReadOnly();
        assertThrows(ClassCastException.class, () -> strictView.getLong("/number"));
        assertThrows(ClassCastException.class, () -> strictView.getString("/text"));
        assertThrows(BranchAsALeafException.class, () -> strictView.getLong("/branch"));
        assertThrows(LeafAsABranchException.class, () -> strictView.getLong("/text/leaf"));
        assertThrows(ClassCastException.class, () -> strictView.getInt("/tooBig"));

        ReadOnlyMessagePak robust = MessagePak.fromBytes(strict.toBytes()).asReadOnly();
        assertTrue(robust.getLong("/number").isEmpty());
        assertTrue(robust.getString("/text").isEmpty());
        assertTrue(robust.getLong("/branch").isEmpty());
        assertTrue(robust.getLong("/text/leaf").isEmpty());
        assertTrue(robust.getInt("/tooBig").isEmpty());
        for (ReadOnlyMessagePak view : List.of(strictView, robust)) {
            for (var missing : List.of(view.getString("/missing"), view.getBoolean("/missing"),
                    view.getInt("/missing"), view.getLong("/missing"), view.getShort("/missing"),
                    view.getFloat("/missing"), view.getDouble("/missing"), view.getDate("/missing"),
                    view.getByteArray("/missing"), view.getIntArray("/missing"),
                    view.getLongArray("/missing"), view.getDoubleArray("/missing"),
                    view.getShortArray("/missing"), view.getStringArray("/missing"),
                    view.getObjectArray("/missing"), view.getValue("/missing"), view.get("/missing"))) {
                assertTrue(missing.isEmpty());
            }
        }
    }

    @Test
    void safelyCopiesSubtypeArraysAndRetainsCompatibleArrayTypes() {
        MessagePak pack = MessagePak.newEmpty();
        TreeMap<String, Object> map = new TreeMap<>();
        map.put("bytes", new byte[] {1});
        TreeMap<?, ?>[] maps = {map};
        java.sql.Timestamp[] dates = {new java.sql.Timestamp(123)};
        String[] strings = {"original"};
        pack.putObjectArray("/maps", maps);
        pack.putObjectArray("/dates", dates);
        pack.putObjectArray("/strings", strings);
        ReadOnlyMessagePak view = pack.asReadOnly();

        Map<?, ?> detachedMap = (Map<?, ?>) view.getObjectArray("/maps").orElseThrow()[0];
        ((byte[]) detachedMap.get("bytes"))[0] = 99;
        ((Date) ((Object[]) view.getValue("/dates").orElseThrow())[0]).setTime(99);
        String[] detachedStrings = assertInstanceOf(String[].class,
                view.getObjectArray("/strings").orElseThrow());
        detachedStrings[0] = "changed";

        assertEquals(1, ((byte[]) map.get("bytes"))[0]);
        assertEquals(123, dates[0].getTime());
        assertEquals("original", strings[0]);
        assertSame(maps, pack.getObjectArray("/maps").orElseThrow());
        assertSame(dates, pack.getObjectArray("/dates").orElseThrow());
    }

    @Test
    void rejectsUnsupportedObjectsWithoutChangingTheOriginalPack() {
        MessagePak pack = MessagePak.newEmpty();
        StringBuilder unsupported = new StringBuilder("mutable");
        Object[] values = {unsupported};
        pack.putObjectArray("/objects", values);

        assertSame(values, pack.getObjectArray("/objects").orElseThrow());
        ReadOnlyMessagePak view = pack.asReadOnly();
        assertThrows(IllegalArgumentException.class, () -> view.getObjectArray("/objects"));
        assertThrows(IllegalArgumentException.class, () -> view.getValue("/objects"));
    }
}

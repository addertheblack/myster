package com.myster.mml;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;

class TestMessagePakDecodeLimits {
    @Test
    void declaredSizesAreRejectedByBudgetBeforeReadingPayload() throws IOException {
        for (String kind : new String[] {"array", "map", "binary", "string", "key"}) {
            for (boolean nested : new boolean[] {false, true}) {
                try (MessageBufferPacker packer = MessagePack.newDefaultBufferPacker()) {
                    packer.packMapHeader(1);
                    if (!kind.equals("key")) {
                        packer.packString("value");
                        if (nested) {
                            packer.packArrayHeader(1);
                        }
                    }
                    switch (kind) {
                        case "array" -> packer.packArrayHeader(1 << 20);
                        case "map" -> packer.packMapHeader(Integer.MAX_VALUE);
                        case "binary" -> packer.packBinaryHeader(1 << 20);
                        case "string", "key" -> packer.packRawStringHeader(1 << 20);
                        default -> throw new AssertionError(kind);
                    }
                    byte[] bytes = packer.toByteArray();
                    IOException failure = assertThrows(IOException.class,
                            () -> MessagePak.fromBytes(bytes, Integer.MAX_VALUE));
                    assertTrue(failure.getMessage().contains("budget"), kind);
                }
            }
        }
    }

    @Test
    void nestedArraysShareBudgetInsteadOfReusingRemainingBytes() throws IOException {
        try (MessageBufferPacker packer = MessagePack.newDefaultBufferPacker()) {
            packer.packMapHeader(1);
            packer.packString("a");
            packer.packArrayHeader(40);
            packer.packArrayHeader(40);
            for (int i = 0; i < 60; i++) {
                packer.packNil();
            }
            IOException failure = assertThrows(IOException.class,
                    () -> MessagePak.fromBytes(packer.toByteArray()));
            assertTrue(failure.getMessage().contains("budget"));
        }
    }

    @Test
    void nestingLimitAcceptsBoundaryAndRejectsNextContainer() throws IOException {
        for (boolean maps : new boolean[] {false, true}) {
            MessagePak.fromBytes(nestedMessage(128, maps));
            IOException failure = assertThrows(IOException.class,
                    () -> MessagePak.fromBytes(nestedMessage(129, maps)));
            assertTrue(failure.getMessage().contains("nesting"));
        }
    }

    @Test
    void byteLimitAcceptsExactSizeAndRejectsOversizedInput() throws IOException {
        MessagePak source = MessagePak.newEmpty();
        source.putString("/name", "Music 🎵");
        source.putByteArray("/bytes", new byte[] {1, 2, 3});
        source.putObjectArray("/nested", new Object[] {
                new Object[] {1L, "", new byte[0]}, true, null});
        byte[] bytes = source.toBytes();
        MessagePak decoded = MessagePak.fromBytes(bytes, bytes.length);
        assertEquals("Music 🎵", decoded.getString("/name").orElseThrow());
        assertArrayEquals(new byte[] {1, 2, 3}, decoded.getByteArray("/bytes").orElseThrow());
        Object[] nested = decoded.getObjectArray("/nested").orElseThrow();
        assertArrayEquals(new Object[] {1L, "", new byte[0]}, (Object[]) nested[0]);
        assertThrows(IOException.class, () -> MessagePak.fromBytes(bytes, bytes.length - 1));
        assertThrows(IllegalArgumentException.class, () -> MessagePak.fromBytes(bytes, -1));
    }

    private static byte[] nestedMessage(int depth, boolean maps) throws IOException {
        try (MessageBufferPacker packer = MessagePack.newDefaultBufferPacker()) {
            packer.packMapHeader(1);
            packer.packString("a");
            for (int i = 1; i < depth; i++) {
                if (maps) {
                    packer.packMapHeader(1);
                    packer.packString("a");
                } else {
                    packer.packArrayHeader(1);
                }
            }
            packer.packNil();
            return packer.toByteArray();
        }
    }
}

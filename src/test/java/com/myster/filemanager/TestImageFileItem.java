package com.myster.filemanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import com.myster.mml.MessagePak;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestImageFileItem {
    @TempDir
    Path tempDir;

    @Test
    void getMessagePackRepresentation_usesInjectedProvider() throws Exception {
        Path file = Files.writeString(tempDir.resolve("photo.jpg"), "content");
        MetadataProvider provider = (metadataType, messagePack, path) -> {
            assertEquals(MetadataType.IMAGE, metadataType);
            assertEquals(file, path);
            assertTrue(messagePack.getLong("/size").isPresent());
            messagePack.putLong("/ImageWidth", 640);
        };

        MessagePak mp = new ImageFileItem(tempDir, file, provider).getMessagePackRepresentation();

        assertEquals(Files.size(file), mp.getLong("/size").orElseThrow());
        assertEquals(640L, mp.getLong("/ImageWidth").orElseThrow());
    }

    @Test
    void getMessagePackRepresentation_keepsMessagePakRamCache() throws Exception {
        Path file = Files.writeString(tempDir.resolve("photo.jpg"), "content");
        AtomicInteger calls = new AtomicInteger();
        MetadataProvider provider = (metadataType, messagePack, path) -> {
            calls.incrementAndGet();
            messagePack.putLong("/ImageWidth", 640);
        };
        ImageFileItem item = new ImageFileItem(tempDir, file, provider);

        MessagePak first = item.getMessagePackRepresentation();
        MessagePak second = item.getMessagePackRepresentation();

        assertSame(first, second);
        assertEquals(1, calls.get());
    }

    @Test
    void getMessagePackRepresentation_returnsGenericStatsWhenProviderWritesNothing()
            throws Exception {
        Path file = Files.writeString(tempDir.resolve("photo.jpg"), "content");
        MetadataProvider provider = (metadataType, messagePack, path) -> {};

        MessagePak mp = new ImageFileItem(tempDir, file, provider).getMessagePackRepresentation();

        assertEquals(Files.size(file), mp.getLong("/size").orElseThrow());
        assertTrue(mp.getLong("/ImageWidth").isEmpty());
    }
}

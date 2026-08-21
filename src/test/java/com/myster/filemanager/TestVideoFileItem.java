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

class TestVideoFileItem {
    @TempDir
    Path tempDir;

    @Test
    void getMessagePackRepresentation_usesVideoExtractor() throws Exception {
        Path file = Files.writeString(tempDir.resolve("clip.mp4"), "content");
        FileMetadataExtractor extractor = (metadataType, messagePack, path) -> {
            assertSame(MetadataType.VIDEO, metadataType);
            assertEquals(file, path);
            assertTrue(messagePack.getLong("/size").isPresent());
            messagePack.putLong("/VideoWidth", 1920);
        };

        MessagePak metadata = new VideoFileItem(tempDir, file, extractor)
                .getMessagePackRepresentation();

        assertEquals(Files.size(file), metadata.getLong("/size").orElseThrow());
        assertEquals(1920L, metadata.getLong("/VideoWidth").orElseThrow());
    }

    @Test
    void getMessagePackRepresentation_enrichesOnce() throws Exception {
        Path file = Files.writeString(tempDir.resolve("clip.mp4"), "content");
        AtomicInteger calls = new AtomicInteger();
        VideoFileItem item = new VideoFileItem(tempDir, file,
                (metadataType, messagePack, path) -> calls.incrementAndGet());

        MessagePak first = item.getMessagePackRepresentation();
        MessagePak second = item.getMessagePackRepresentation();

        assertSame(first, second);
        assertEquals(1, calls.get());
    }

    @Test
    void getMessagePackRepresentation_preservesGenericMetadataWhenVideoDataIsUnavailable()
            throws Exception {
        Path file = Files.writeString(tempDir.resolve("clip.mkv"), "content");
        VideoFileItem item = new VideoFileItem(tempDir, file,
                (metadataType, messagePack, path) -> {});

        MessagePak metadata = item.getMessagePackRepresentation();

        assertEquals(Files.size(file), metadata.getLong("/size").orElseThrow());
        assertTrue(metadata.getLong("/VideoLengthSec").isEmpty());
    }
}

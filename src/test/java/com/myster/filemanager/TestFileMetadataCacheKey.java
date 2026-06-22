package com.myster.filemanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestFileMetadataCacheKey {
    @TempDir
    Path tempDir;

    @Test
    void sameFileStateSameKey() throws IOException {
        Path file = Files.writeString(tempDir.resolve("song.mp3"), "content");

        FileMetadataCacheKey first = FileMetadataCacheKey.from(MetadataType.AUDIO, file,
                Files.size(file));
        FileMetadataCacheKey second = FileMetadataCacheKey.from(MetadataType.AUDIO, file,
                Files.size(file));

        assertEquals(first, second);
        assertEquals(MetadataType.AUDIO.cacheKey(), first.metadataType());
        assertEquals(first.entryKey(), second.entryKey());
        assertEquals(first.shardId(), second.shardId());
    }

    @Test
    void differentPathDoesNotReuseEntry() throws IOException {
        Path firstFile = Files.writeString(tempDir.resolve("first-song.mp3"), "content");
        Path secondFile = Files.writeString(tempDir.resolve("second-song.mp3"), "content");

        FileMetadataCacheKey first = FileMetadataCacheKey.from(MetadataType.AUDIO, firstFile,
                Files.size(firstFile));
        FileMetadataCacheKey second = FileMetadataCacheKey.from(MetadataType.AUDIO, secondFile,
                Files.size(secondFile));

        assertNotEquals(first.entryKey(), second.entryKey());
    }

    @Test
    void changedSizeOrMtimeInvalidates() throws IOException {
        Path file = Files.writeString(tempDir.resolve("song.mp3"), "content");
        FileMetadataCacheKey original = FileMetadataCacheKey.from(MetadataType.AUDIO, file,
                Files.size(file));

        FileMetadataCacheKey changedSize = FileMetadataCacheKey.from(MetadataType.AUDIO, file,
                Files.size(file) + 1);
        assertNotEquals(original, changedSize);

        Files.setLastModifiedTime(file,
                FileTime.fromMillis(Files.getLastModifiedTime(file).toMillis() + 10_000));
        FileMetadataCacheKey changedMtime = FileMetadataCacheKey.from(MetadataType.AUDIO, file,
                Files.size(file));
        assertNotEquals(original, changedMtime);
    }
}

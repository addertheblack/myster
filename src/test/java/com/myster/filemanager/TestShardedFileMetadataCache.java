package com.myster.filemanager;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import com.myster.mml.MessagePak;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestShardedFileMetadataCache {
    @TempDir
    Path tempDir;

    @Test
    void putThenGet() throws IOException {
        Path file = Files.writeString(tempDir.resolve("song.mp3"), "content");
        FileMetadataCacheKey key = FileMetadataCacheKey.from(MetadataType.AUDIO, file,
                Files.size(file));
        ShardedFileMetadataCache cache = new ShardedFileMetadataCache(tempDir.resolve("cache"));

        cache.put(key, metadataWithLength(123));

        MessagePak cached = cache.get(key).orElseThrow();
        assertEquals(123L, cached.getLong("/LengthSec").orElseThrow());
    }

    @Test
    void getReturnsEmptyForStaleSize() throws IOException {
        Path file = Files.writeString(tempDir.resolve("song.mp3"), "content");
        FileMetadataCacheKey key = FileMetadataCacheKey.from(MetadataType.AUDIO, file,
                Files.size(file));
        ShardedFileMetadataCache cache = new ShardedFileMetadataCache(tempDir.resolve("cache"));
        cache.put(key, metadataWithLength(123));

        FileMetadataCacheKey staleSize = FileMetadataCacheKey.from(MetadataType.AUDIO, file,
                Files.size(file) + 1);

        assertTrue(cache.get(staleSize).isEmpty());
    }

    @Test
    void getReturnsEmptyForStaleMtime() throws IOException {
        Path file = Files.writeString(tempDir.resolve("song.mp3"), "content");
        FileMetadataCacheKey key = FileMetadataCacheKey.from(MetadataType.AUDIO, file,
                Files.size(file));
        ShardedFileMetadataCache cache = new ShardedFileMetadataCache(tempDir.resolve("cache"));
        cache.put(key, metadataWithLength(123));

        Files.setLastModifiedTime(file,
                FileTime.fromMillis(Files.getLastModifiedTime(file).toMillis() + 10_000));
        FileMetadataCacheKey staleMtime = FileMetadataCacheKey.from(MetadataType.AUDIO, file,
                Files.size(file));

        assertTrue(cache.get(staleMtime).isEmpty());
    }

    @Test
    void corruptShardIsMiss() throws IOException {
        Path file = Files.writeString(tempDir.resolve("song.mp3"), "content");
        FileMetadataCacheKey key = FileMetadataCacheKey.from(MetadataType.AUDIO, file,
                Files.size(file));
        Path cacheRoot = tempDir.resolve("cache");
        Files.createDirectories(cacheRoot.resolve("v1"));
        Files.writeString(cacheRoot.resolve("v1").resolve(key.shardId() + ".mpak"), "bad data");

        ShardedFileMetadataCache cache = new ShardedFileMetadataCache(cacheRoot);

        assertDoesNotThrow(() -> assertTrue(cache.get(key).isEmpty()));
        cache.put(key, metadataWithLength(456));
        assertEquals(456L, cache.get(key).orElseThrow().getLong("/LengthSec").orElseThrow());
    }

    @Test
    void expiredEntryIsMiss() throws IOException {
        Path file = Files.writeString(tempDir.resolve("song.mp3"), "content");
        FileMetadataCacheKey key = FileMetadataCacheKey.from(MetadataType.AUDIO, file,
                Files.size(file));
        Path cacheRoot = tempDir.resolve("cache");
        Clock oldClock = Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC);
        Clock newClock = Clock.fixed(Instant.parse("2025-08-01T00:00:00Z"), ZoneOffset.UTC);

        new ShardedFileMetadataCache(cacheRoot, oldClock).put(key, metadataWithLength(123));

        assertTrue(new ShardedFileMetadataCache(cacheRoot, newClock).get(key).isEmpty());
    }

    @Test
    void expiredEntryIsPrunedOnNextWriteToShard() throws IOException {
        Path oldFile = Files.writeString(tempDir.resolve("old-song.mp3"), "content");
        FileMetadataCacheKey oldKey = FileMetadataCacheKey.from(MetadataType.AUDIO, oldFile,
                Files.size(oldFile));
        FileMetadataCacheKey newKey = newKeyInShard(oldKey.shardId());
        Path cacheRoot = tempDir.resolve("cache");
        Clock oldClock = Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC);
        Clock newClock = Clock.fixed(Instant.parse("2025-08-01T00:00:00Z"), ZoneOffset.UTC);
        ShardedFileMetadataCache cache = new ShardedFileMetadataCache(cacheRoot, oldClock);
        cache.put(oldKey, metadataWithLength(123));

        cache = new ShardedFileMetadataCache(cacheRoot, newClock);
        assertTrue(cache.get(oldKey).isEmpty());
        assertTrue(shardData(cacheRoot, oldKey).isADirectory(entryPath(oldKey)));

        cache.put(newKey, metadataWithLength(456));

        assertFalse(shardData(cacheRoot, oldKey).isADirectory(entryPath(oldKey)));
        assertEquals(456L, cache.get(newKey).orElseThrow().getLong("/LengthSec").orElseThrow());
    }

    @Test
    void concurrentDifferentShards() throws Exception {
        ShardedFileMetadataCache cache = new ShardedFileMetadataCache(tempDir.resolve("cache"));
        List<FileMetadataCacheKey> keys = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            Path file = Files.writeString(tempDir.resolve("song-" + i + ".mp3"), "content-" + i);
            keys.add(FileMetadataCacheKey.from(MetadataType.AUDIO, file, Files.size(file)));
        }

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<Void>> calls = keys.stream()
                    .<Callable<Void>>map(key -> () -> {
                        cache.put(key, metadataWithLength(100));
                        assertEquals(100L,
                                cache.get(key).orElseThrow().getLong("/LengthSec").orElseThrow());
                        return null;
                    })
                    .toList();
            for (var future : executor.invokeAll(calls)) {
                future.get();
            }
        }
    }

    @Test
    void sameShardWritesSerialize() throws Exception {
        Path file = Files.writeString(tempDir.resolve("song.mp3"), "content");
        FileMetadataCacheKey key = FileMetadataCacheKey.from(MetadataType.AUDIO, file,
                Files.size(file));
        ShardedFileMetadataCache cache = new ShardedFileMetadataCache(tempDir.resolve("cache"));

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<Void>> calls = List.of(
                    () -> {
                        cache.put(key, metadataWithLength(111));
                        return null;
                    },
                    () -> {
                        cache.put(key, metadataWithLength(222));
                        return null;
                    });
            for (var future : executor.invokeAll(calls)) {
                future.get();
            }
        }

        long length = cache.get(key).orElseThrow().getLong("/LengthSec").orElseThrow();
        assertTrue(length == 111L || length == 222L);
    }

    @Test
    void sameShardReadersDoNotCorruptData() throws Exception {
        Path file = Files.writeString(tempDir.resolve("song.mp3"), "content");
        FileMetadataCacheKey key = FileMetadataCacheKey.from(MetadataType.AUDIO, file,
                Files.size(file));
        ShardedFileMetadataCache cache = new ShardedFileMetadataCache(tempDir.resolve("cache"));
        cache.put(key, metadataWithLength(333));

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<Void>> calls = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                calls.add(() -> {
                    assertEquals(333L,
                            cache.get(key).orElseThrow().getLong("/LengthSec").orElseThrow());
                    return null;
                });
            }
            for (var future : executor.invokeAll(calls)) {
                future.get();
            }
        }
    }

    private static MessagePak metadataWithLength(long length) {
        MessagePak metadata = MessagePak.newEmpty();
        metadata.putLong("/LengthSec", length);
        return metadata;
    }

    private FileMetadataCacheKey newKeyInShard(String shardId) throws IOException {
        for (int i = 0; i < 10_000; i++) {
            Path file = Files.writeString(tempDir.resolve("same-shard-" + i + ".mp3"),
                    "content-" + i);
            FileMetadataCacheKey key = FileMetadataCacheKey.from(MetadataType.AUDIO, file,
                    Files.size(file));
            if (shardId.equals(key.shardId())) {
                return key;
            }
        }

        throw new AssertionError("Could not find test key in shard " + shardId);
    }

    private static MessagePak shardData(Path cacheRoot, FileMetadataCacheKey key) throws IOException {
        return MessagePak.fromBytes(Files.readAllBytes(cacheRoot.resolve("v1")
                .resolve(key.shardId() + ".mpak")));
    }

    private static String entryPath(FileMetadataCacheKey key) {
        return "/entries/" + key.entryKey() + "/";
    }
}

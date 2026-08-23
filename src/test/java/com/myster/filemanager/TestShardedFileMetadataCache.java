package com.myster.filemanager;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import com.myster.mml.MessagePak;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestShardedFileMetadataCache {
    @TempDir
    Path tempDir;

    @Test
    void putIsImmediatelyReadableAndReplaysInFreshCache() throws IOException {
        Path file = Files.writeString(tempDir.resolve("song.mp3"), "content");
        FileMetadataCacheKey key = FileMetadataCacheKey.from(MetadataType.AUDIO, file,
                Files.size(file));
        Path cacheRoot = tempDir.resolve("cache");
        ShardedFileMetadataCache cache = new ShardedFileMetadataCache(cacheRoot);

        cache.put(key, metadataWithLength(123));

        assertEquals(123L, cache.get(key).orElseThrow().getLong("/LengthSec").orElseThrow());
        assertEquals(123L, new ShardedFileMetadataCache(cacheRoot).get(key).orElseThrow()
                .getLong("/LengthSec").orElseThrow());
    }

    @Test
    void repeatedPutAppendsAndLatestValueWins() throws IOException {
        Path file = Files.writeString(tempDir.resolve("song.mp3"), "content");
        FileMetadataCacheKey key = FileMetadataCacheKey.from(MetadataType.AUDIO, file,
                Files.size(file));
        Path cacheRoot = tempDir.resolve("cache");
        ShardedFileMetadataCache cache = new ShardedFileMetadataCache(cacheRoot);
        cache.put(key, metadataWithLength(111));
        byte[] firstWrite = Files.readAllBytes(shardLogPath(cacheRoot, key));

        cache.put(key, metadataWithLength(222));

        byte[] secondWrite = Files.readAllBytes(shardLogPath(cacheRoot, key));
        assertTrue(secondWrite.length > firstWrite.length);
        assertArrayEquals(firstWrite, java.util.Arrays.copyOf(secondWrite, firstWrite.length));
        assertEquals(222L, new ShardedFileMetadataCache(cacheRoot).get(key).orElseThrow()
                .getLong("/LengthSec").orElseThrow());
    }

    @Test
    void emptyMetadataPersistsAsNegativeCacheHit() throws IOException {
        Path file = Files.writeString(tempDir.resolve("clip.avi"), "content");
        FileMetadataCacheKey key = FileMetadataCacheKey.from(MetadataType.VIDEO, file,
                Files.size(file));
        Path cacheRoot = tempDir.resolve("cache");
        new ShardedFileMetadataCache(cacheRoot).put(key, MessagePak.newEmpty());

        Optional<MessagePak> cached = new ShardedFileMetadataCache(cacheRoot).get(key);

        assertTrue(cached.isPresent());
        assertTrue(MessagePakTreeUtils.isEmpty(cached.orElseThrow()));
    }

    @Test
    void cacheVersionMismatchRescansWithoutMovingEntry() throws IOException {
        Path file = Files.writeString(tempDir.resolve("song.mp3"), "content");
        FileMetadataCacheKey versionOne = FileMetadataCacheKey.from(
                "audio", 1, file, Files.size(file));
        FileMetadataCacheKey versionTwo = FileMetadataCacheKey.from(
                "audio", 2, file, Files.size(file));
        Path cacheRoot = tempDir.resolve("cache");
        ShardedFileMetadataCache cache = new ShardedFileMetadataCache(cacheRoot);
        cache.put(versionOne, metadataWithLength(111));

        assertTrue(new ShardedFileMetadataCache(cacheRoot).get(versionTwo).isEmpty());

        cache.put(versionTwo, metadataWithLength(222));
        ShardedFileMetadataCache reloaded = new ShardedFileMetadataCache(cacheRoot);
        assertEquals(versionOne.entryKey(), versionTwo.entryKey());
        assertEquals(222L, reloaded.get(versionTwo).orElseThrow()
                .getLong("/LengthSec").orElseThrow());
        assertTrue(reloaded.get(versionOne).isEmpty());
    }

    @Test
    void negativeEntryAlsoRequiresCurrentCacheVersion() throws IOException {
        Path file = Files.writeString(tempDir.resolve("clip.avi"), "content");
        FileMetadataCacheKey versionOne = FileMetadataCacheKey.from(
                "video", 1, file, Files.size(file));
        FileMetadataCacheKey versionTwo = FileMetadataCacheKey.from(
                "video", 2, file, Files.size(file));
        Path cacheRoot = tempDir.resolve("cache");
        new ShardedFileMetadataCache(cacheRoot).put(versionOne, MessagePak.newEmpty());

        assertTrue(new ShardedFileMetadataCache(cacheRoot).get(versionTwo).isEmpty());
    }

    @Test
    void versionChangeDoesNotInvalidateAnotherTypeInSameShard() throws IOException {
        Path audioFile = Files.writeString(tempDir.resolve("song.mp3"), "content");
        FileMetadataCacheKey audioV1 = FileMetadataCacheKey.from(
                "audio", 1, audioFile, Files.size(audioFile));
        FileMetadataCacheKey image = newKeyInShard("image", 1, audioV1.shardId());
        Path cacheRoot = tempDir.resolve("cache");
        ShardedFileMetadataCache cache = new ShardedFileMetadataCache(cacheRoot);
        cache.put(audioV1, metadataWithLength(111));
        MessagePak imageMetadata = MessagePak.newEmpty();
        imageMetadata.putLong("/ImageWidth", 640);
        cache.put(image, imageMetadata);

        FileMetadataCacheKey audioV2 = FileMetadataCacheKey.from(
                "audio", 2, audioFile, Files.size(audioFile));
        ShardedFileMetadataCache reloaded = new ShardedFileMetadataCache(cacheRoot);

        assertTrue(reloaded.get(audioV2).isEmpty());
        assertEquals(640L, reloaded.get(image).orElseThrow()
                .getLong("/ImageWidth").orElseThrow());
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
    void legacyWholeShardIsIgnored() throws IOException {
        Path file = Files.writeString(tempDir.resolve("song.mp3"), "content");
        FileMetadataCacheKey key = FileMetadataCacheKey.from(MetadataType.AUDIO, file,
                Files.size(file));
        Path cacheRoot = tempDir.resolve("cache");
        Path legacyDirectory = cacheRoot.resolve("v1");
        Files.createDirectories(legacyDirectory);
        Files.write(legacyDirectory.resolve(key.shardId() + ".mpak"),
                metadataWithLength(999).toBytes());

        assertTrue(new ShardedFileMetadataCache(cacheRoot).get(key).isEmpty());
        assertFalse(Files.exists(shardLogPath(cacheRoot, key)));
    }

    @Test
    void corruptShardIsMissAndNextPutRecreatesIt() throws IOException {
        Path file = Files.writeString(tempDir.resolve("song.mp3"), "content");
        FileMetadataCacheKey key = FileMetadataCacheKey.from(MetadataType.AUDIO, file,
                Files.size(file));
        Path cacheRoot = tempDir.resolve("cache");
        Files.createDirectories(cacheRoot.resolve("v2"));
        Files.writeString(shardLogPath(cacheRoot, key), "bad data");

        ShardedFileMetadataCache cache = new ShardedFileMetadataCache(cacheRoot);

        assertDoesNotThrow(() -> assertTrue(cache.get(key).isEmpty()));
        cache.put(key, metadataWithLength(456));
        assertEquals(MetadataCacheLog.ReplayStatus.VALID,
                cacheLog(cacheRoot, key).replay().status());
        assertEquals(456L, new ShardedFileMetadataCache(cacheRoot).get(key).orElseThrow()
                .getLong("/LengthSec").orElseThrow());
    }

    @Test
    void malformedOperationRecordInvalidatesShard() throws IOException {
        Path file = Files.writeString(tempDir.resolve("song.mp3"), "content");
        FileMetadataCacheKey key = FileMetadataCacheKey.from(MetadataType.AUDIO, file,
                Files.size(file));
        Path cacheRoot = tempDir.resolve("cache");
        MetadataCacheLog log = cacheLog(cacheRoot, key);
        MessagePak malformed = MessagePak.newEmpty();
        malformed.putString("/unexpected", "value");
        log.append(0, 1, malformed);

        assertTrue(new ShardedFileMetadataCache(cacheRoot).get(key).isEmpty());
        assertFalse(Files.exists(log.path()));
    }

    @Test
    void removeAppendsTombstoneAndPersists() throws IOException {
        Path file = Files.writeString(tempDir.resolve("song.mp3"), "content");
        FileMetadataCacheKey key = FileMetadataCacheKey.from(MetadataType.AUDIO, file,
                Files.size(file));
        Path cacheRoot = tempDir.resolve("cache");
        ShardedFileMetadataCache cache = new ShardedFileMetadataCache(cacheRoot);
        cache.put(key, metadataWithLength(123));
        long beforeRemove = Files.size(shardLogPath(cacheRoot, key));

        cache.remove(key);

        assertTrue(Files.size(shardLogPath(cacheRoot, key)) > beforeRemove);
        assertTrue(cache.get(key).isEmpty());
        assertTrue(new ShardedFileMetadataCache(cacheRoot).get(key).isEmpty());
    }

    @Test
    void expiredEntryIsMissAndCompactedOnReplay() throws IOException {
        Path file = Files.writeString(tempDir.resolve("song.mp3"), "content");
        FileMetadataCacheKey key = FileMetadataCacheKey.from(MetadataType.AUDIO, file,
                Files.size(file));
        Path cacheRoot = tempDir.resolve("cache");
        Clock oldClock = Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC);
        Clock newClock = Clock.fixed(Instant.parse("2025-08-01T00:00:00Z"), ZoneOffset.UTC);
        new ShardedFileMetadataCache(cacheRoot, oldClock).put(key, metadataWithLength(123));

        assertTrue(new ShardedFileMetadataCache(cacheRoot, newClock).get(key).isEmpty());

        MetadataCacheLog.ReplayResult replay = cacheLog(cacheRoot, key).replay();
        assertEquals(MetadataCacheLog.ReplayStatus.VALID, replay.status());
        assertTrue(replay.frames().isEmpty());
    }

    @Test
    void truncatedFinalRecordPreservesPrefixAndIsRepairedByNextPut() throws IOException {
        Path firstFile = Files.writeString(tempDir.resolve("first.mp3"), "first");
        FileMetadataCacheKey first = FileMetadataCacheKey.from(
                MetadataType.AUDIO, firstFile, Files.size(firstFile));
        FileMetadataCacheKey second = newKeyInShard("audio", 1, first.shardId());
        Path cacheRoot = tempDir.resolve("cache");
        ShardedFileMetadataCache cache = new ShardedFileMetadataCache(cacheRoot);
        cache.put(first, metadataWithLength(111));
        cache.put(second, metadataWithLength(222));
        MetadataCacheLog log = cacheLog(cacheRoot, first);
        MetadataCacheLog.Frame lastFrame = log.replay().frames().getLast();
        try (var channel = java.nio.channels.FileChannel.open(log.path(),
                java.nio.file.StandardOpenOption.WRITE)) {
            channel.truncate(lastFrame.startOffset() + 5);
        }

        ShardedFileMetadataCache recovered = new ShardedFileMetadataCache(cacheRoot);
        assertEquals(111L, recovered.get(first).orElseThrow()
                .getLong("/LengthSec").orElseThrow());
        assertTrue(recovered.get(second).isEmpty());

        recovered.put(second, metadataWithLength(333));
        assertEquals(MetadataCacheLog.ReplayStatus.VALID, log.replay().status());
        assertEquals(333L, new ShardedFileMetadataCache(cacheRoot).get(second).orElseThrow()
                .getLong("/LengthSec").orElseThrow());
    }

    @Test
    void compactionDropsSupersededRecords() throws IOException {
        Path file = Files.writeString(tempDir.resolve("song.mp3"), "content");
        FileMetadataCacheKey key = FileMetadataCacheKey.from(MetadataType.AUDIO, file,
                Files.size(file));
        Path cacheRoot = tempDir.resolve("cache");
        ShardedFileMetadataCache cache = new ShardedFileMetadataCache(
                cacheRoot,
                Clock.systemUTC(),
                new ShardedFileMetadataCache.CompactionPolicy(0, 2));

        cache.put(key, metadataWithLength(111));
        cache.put(key, metadataWithLength(222));
        cache.put(key, metadataWithLength(333));

        MetadataCacheLog.ReplayResult replay = cacheLog(cacheRoot, key).replay();
        assertEquals(1, replay.frames().size());
        assertEquals(1L, replay.frames().getFirst().sequence());
        assertEquals(333L, new ShardedFileMetadataCache(cacheRoot).get(key).orElseThrow()
                .getLong("/LengthSec").orElseThrow());
    }

    @Test
    void flushDoesNotRewriteLog() throws IOException {
        Path file = Files.writeString(tempDir.resolve("song.mp3"), "content");
        FileMetadataCacheKey key = FileMetadataCacheKey.from(MetadataType.AUDIO, file,
                Files.size(file));
        Path cacheRoot = tempDir.resolve("cache");
        ShardedFileMetadataCache cache = new ShardedFileMetadataCache(cacheRoot);
        cache.put(key, metadataWithLength(123));
        byte[] beforeFlush = Files.readAllBytes(shardLogPath(cacheRoot, key));

        cache.flush();

        assertArrayEquals(beforeFlush, Files.readAllBytes(shardLogPath(cacheRoot, key)));
    }

    @Test
    void concurrentDifferentShardsReplay() throws Exception {
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

        ShardedFileMetadataCache reloaded = new ShardedFileMetadataCache(tempDir.resolve("cache"));
        for (FileMetadataCacheKey key : keys) {
            assertEquals(100L, reloaded.get(key).orElseThrow()
                    .getLong("/LengthSec").orElseThrow());
        }
    }

    @Test
    void sameShardWritesProduceCompleteFrames() throws Exception {
        Path file = Files.writeString(tempDir.resolve("song.mp3"), "content");
        FileMetadataCacheKey key = FileMetadataCacheKey.from(MetadataType.AUDIO, file,
                Files.size(file));
        Path cacheRoot = tempDir.resolve("cache");
        ShardedFileMetadataCache cache = new ShardedFileMetadataCache(cacheRoot);

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

        MetadataCacheLog.ReplayResult replay = cacheLog(cacheRoot, key).replay();
        assertEquals(MetadataCacheLog.ReplayStatus.VALID, replay.status());
        assertEquals(2, replay.frames().size());
        long length = new ShardedFileMetadataCache(cacheRoot).get(key).orElseThrow()
                .getLong("/LengthSec").orElseThrow();
        assertTrue(length == 111L || length == 222L);
    }

    private static MessagePak metadataWithLength(long length) {
        MessagePak metadata = MessagePak.newEmpty();
        metadata.putLong("/LengthSec", length);
        return metadata;
    }

    private FileMetadataCacheKey newKeyInShard(String metadataTypeId,
                                               int cacheVersion,
                                               String shardId) throws IOException {
        for (int i = 0; i < 10_000; i++) {
            Path file = Files.writeString(tempDir.resolve(
                    metadataTypeId + "-same-shard-" + i + ".bin"), "content-" + i);
            FileMetadataCacheKey key = FileMetadataCacheKey.from(
                    metadataTypeId, cacheVersion, file, Files.size(file));
            if (shardId.equals(key.shardId())) {
                return key;
            }
        }
        throw new AssertionError("Could not find test key in shard " + shardId);
    }

    private static MetadataCacheLog cacheLog(Path cacheRoot, FileMetadataCacheKey key) {
        return new MetadataCacheLog(shardLogPath(cacheRoot, key),
                Integer.parseInt(key.shardId(), 16));
    }

    private static Path shardLogPath(Path cacheRoot, FileMetadataCacheKey key) {
        return cacheRoot.resolve("v2").resolve(key.shardId() + ".mlog");
    }
}

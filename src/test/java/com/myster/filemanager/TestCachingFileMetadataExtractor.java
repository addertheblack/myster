package com.myster.filemanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import com.myster.mml.MessagePak;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestCachingFileMetadataExtractor {
    @TempDir
    Path tempDir;

    @Test
    void cacheHitDoesNotCallDelegate() throws IOException {
        Path file = Files.writeString(tempDir.resolve("song.mp3"), "content");
        RecordingCache cache = new RecordingCache();
        cache.getResult = Optional.of(metadataWithLength(123));
        AtomicInteger delegateCalls = new AtomicInteger();
        CachingFileMetadataExtractor extractor = new CachingFileMetadataExtractor(cache,
                (metadataType, messagePack, path) -> delegateCalls.incrementAndGet());
        MessagePak messagePack = filePack(file);

        extractor.enrich(MetadataType.AUDIO, messagePack, file);

        assertEquals(0, delegateCalls.get());
        assertEquals(123L, messagePack.getLong("/LengthSec").orElseThrow());
    }

    @Test
    void cacheMissCallsDelegateAndStores() throws IOException {
        Path file = Files.writeString(tempDir.resolve("song.mp3"), "content");
        RecordingCache cache = new RecordingCache();
        AtomicInteger delegateCalls = new AtomicInteger();
        CachingFileMetadataExtractor extractor = new CachingFileMetadataExtractor(cache,
                (metadataType, messagePack, path) -> {
                    delegateCalls.incrementAndGet();
                    assertTrue(messagePack.getLong("/size").isPresent());
                    messagePack.putLong("/LengthSec", 456);
                });
        MessagePak messagePack = filePack(file);

        extractor.enrich(MetadataType.AUDIO, messagePack, file);

        assertEquals(1, delegateCalls.get());
        assertEquals(456L, messagePack.getLong("/LengthSec").orElseThrow());
        assertEquals(456L, cache.putMetadata.getLong("/LengthSec").orElseThrow());
        assertTrue(cache.putMetadata.getLong("/size").isEmpty());
    }

    @Test
    void emptyDelegateResultWritesNegativeCacheEntry() throws IOException {
        Path file = Files.writeString(tempDir.resolve("song.mp3"), "content");
        RecordingCache cache = new RecordingCache();
        CachingFileMetadataExtractor extractor = new CachingFileMetadataExtractor(cache,
                (metadataType, messagePack, path) -> {});

        extractor.enrich(MetadataType.AUDIO, filePack(file), file);

        assertEquals(1, cache.putCalls);
        assertTrue(MessagePakTreeUtils.isEmpty(cache.putMetadata));
    }

    @Test
    void negativeCacheHitDoesNotCallDelegate() throws IOException {
        Path file = Files.writeString(tempDir.resolve("clip.avi"), "content");
        RecordingCache cache = new RecordingCache();
        cache.getResult = Optional.of(MessagePak.newEmpty());
        AtomicInteger delegateCalls = new AtomicInteger();
        CachingFileMetadataExtractor extractor = new CachingFileMetadataExtractor(cache,
                (metadataType, messagePack, path) -> delegateCalls.incrementAndGet());

        extractor.enrich(MetadataType.VIDEO, filePack(file), file);

        assertEquals(0, delegateCalls.get());
        assertEquals(0, cache.putCalls);
    }

    @Test
    void emptyResultIsReusedFromPersistentCache() throws IOException {
        Path file = Files.writeString(tempDir.resolve("clip.avi"), "content");
        Path cacheRoot = tempDir.resolve("cache");
        AtomicInteger delegateCalls = new AtomicInteger();
        FileMetadataExtractor delegate = (metadataType, messagePack, path) ->
                delegateCalls.incrementAndGet();

        new CachingFileMetadataExtractor(new ShardedFileMetadataCache(cacheRoot), delegate)
                .enrich(MetadataType.VIDEO, filePack(file), file);
        new CachingFileMetadataExtractor(new ShardedFileMetadataCache(cacheRoot), delegate)
                .enrich(MetadataType.VIDEO, filePack(file), file);

        assertEquals(1, delegateCalls.get());
    }

    @Test
    void imageMetadataCachesOnlyAllowedImageKeys() throws IOException {
        Path file = Files.writeString(tempDir.resolve("photo.jpg"), "content");
        RecordingCache cache = new RecordingCache();
        CachingFileMetadataExtractor extractor = new CachingFileMetadataExtractor(cache,
                (metadataType, messagePack, path) -> {
                    messagePack.putLong("/ImageWidth", 640);
                    messagePack.putLong("/ImageHeight", 480);
                    messagePack.putLong("/size", 999);
                    messagePack.putLong("/LengthSec", 12);
                });

        extractor.enrich(MetadataType.IMAGE, filePack(file), file);

        assertEquals(1, cache.putCalls);
        assertEquals(640L, cache.putMetadata.getLong("/ImageWidth").orElseThrow());
        assertEquals(480L, cache.putMetadata.getLong("/ImageHeight").orElseThrow());
        assertTrue(cache.putMetadata.getLong("/size").isEmpty());
        assertTrue(cache.putMetadata.getLong("/LengthSec").isEmpty());
    }

    @Test
    void videoMetadataCachesOnlyAllowedVideoKeys() throws IOException {
        Path file = Files.writeString(tempDir.resolve("clip.mp4"), "content");
        RecordingCache cache = new RecordingCache();
        CachingFileMetadataExtractor extractor = new CachingFileMetadataExtractor(cache,
                (metadataType, messagePack, path) -> {
                    messagePack.putLong("/VideoLengthSec", 120);
                    messagePack.putLong("/VideoWidth", 1920);
                    messagePack.putLong("/VideoHeight", 1080);
                    messagePack.putString("/VideoCodec", "H.264");
                    messagePack.putLong("/VideoBitRate", 4_000_000);
                    messagePack.putLong("/ImageWidth", 640);
                });

        extractor.enrich(MetadataType.VIDEO, filePack(file), file);

        assertEquals(1, cache.putCalls);
        assertEquals(120L, cache.putMetadata.getLong("/VideoLengthSec").orElseThrow());
        assertEquals(1920L, cache.putMetadata.getLong("/VideoWidth").orElseThrow());
        assertEquals(1080L, cache.putMetadata.getLong("/VideoHeight").orElseThrow());
        assertEquals("H.264", cache.putMetadata.getString("/VideoCodec").orElseThrow());
        assertEquals(4_000_000L,
                cache.putMetadata.getLong("/VideoBitRate").orElseThrow());
        assertTrue(cache.putMetadata.getLong("/ImageWidth").isEmpty());
        assertTrue(cache.putMetadata.getLong("/size").isEmpty());
    }

    @Test
    void missingSizeThrowsIllegalStateException() {
        RecordingCache cache = new RecordingCache();
        CachingFileMetadataExtractor extractor = new CachingFileMetadataExtractor(cache,
                (metadataType, messagePack, path) -> messagePack.putLong("/LengthSec", 1));

        assertThrows(IllegalStateException.class,
                () -> extractor.enrich(MetadataType.AUDIO, MessagePak.newEmpty(),
                        tempDir.resolve("song.mp3")));
        assertEquals(0, cache.putCalls);
    }

    @Test
    void keyCreationFailureDoesNotCallDelegate() {
        Path missingFile = tempDir.resolve("missing.mp3");
        MessagePak messagePack = MessagePak.newEmpty();
        messagePack.putLong("/size", 123);
        AtomicInteger delegateCalls = new AtomicInteger();
        CachingFileMetadataExtractor extractor = new CachingFileMetadataExtractor(new RecordingCache(),
                (metadataType, mp, path) -> {
                    delegateCalls.incrementAndGet();
                    mp.putLong("/LengthSec", 1);
                });

        extractor.enrich(MetadataType.AUDIO, messagePack, missingFile);

        assertEquals(0, delegateCalls.get());
        assertTrue(messagePack.getLong("/LengthSec").isEmpty());
    }

    private static MessagePak filePack(Path file) throws IOException {
        MessagePak messagePack = MessagePak.newEmpty();
        messagePack.putLong("/size", Files.size(file));
        return messagePack;
    }

    private static MessagePak metadataWithLength(long length) {
        MessagePak metadata = MessagePak.newEmpty();
        metadata.putLong("/LengthSec", length);
        return metadata;
    }

    private static class RecordingCache implements FileMetadataCache {
        Optional<MessagePak> getResult = Optional.empty();
        int putCalls;
        MessagePak putMetadata;

        @Override
        public Optional<MessagePak> get(FileMetadataCacheKey key) {
            return getResult;
        }

        @Override
        public void put(FileMetadataCacheKey key, MessagePak metadata) {
            putCalls++;
            putMetadata = metadata;
        }

        @Override
        public void remove(FileMetadataCacheKey key) {
        }
    }
}

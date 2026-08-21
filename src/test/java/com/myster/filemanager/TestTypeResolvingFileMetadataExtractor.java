package com.myster.filemanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.myster.mml.MessagePak;
import org.junit.jupiter.api.Test;

class TestTypeResolvingFileMetadataExtractor {
    @Test
    void routesByMetadataType() {
        AtomicInteger calls = new AtomicInteger();
        TypeResolvingFileMetadataExtractor extractor = new TypeResolvingFileMetadataExtractor(
                Map.of(MetadataType.AUDIO, (messagePack, path) -> {
                    calls.incrementAndGet();
                    messagePack.putLong("/LengthSec", 12);
                }));
        MessagePak messagePack = MessagePak.newEmpty();

        extractor.enrich(MetadataType.AUDIO, messagePack, Path.of("song.mp3"));

        assertEquals(1, calls.get());
        assertEquals(12L, messagePack.getLong("/LengthSec").orElseThrow());
    }

    @Test
    void routesImageMetadataIndependently() {
        AtomicInteger audioCalls = new AtomicInteger();
        AtomicInteger imageCalls = new AtomicInteger();
        TypeResolvingFileMetadataExtractor extractor = new TypeResolvingFileMetadataExtractor(
                Map.of(MetadataType.AUDIO, (messagePack, path) -> {
                    audioCalls.incrementAndGet();
                    messagePack.putLong("/LengthSec", 12);
                },
                        MetadataType.IMAGE, (messagePack, path) -> {
                            imageCalls.incrementAndGet();
                            messagePack.putLong("/ImageWidth", 640);
                        }));
        MessagePak messagePack = MessagePak.newEmpty();

        extractor.enrich(MetadataType.IMAGE, messagePack, Path.of("photo.jpg"));

        assertEquals(0, audioCalls.get());
        assertEquals(1, imageCalls.get());
        assertEquals(640L, messagePack.getLong("/ImageWidth").orElseThrow());
        assertTrue(messagePack.getLong("/LengthSec").isEmpty());
    }

    @Test
    void unsupportedTypeNoOps() {
        TypeResolvingFileMetadataExtractor extractor = new TypeResolvingFileMetadataExtractor(Map.of());
        MessagePak messagePack = MessagePak.newEmpty();

        extractor.enrich(MetadataType.AUDIO, messagePack, Path.of("song.mp3"));

        assertTrue(messagePack.list("/").isEmpty());
    }
}

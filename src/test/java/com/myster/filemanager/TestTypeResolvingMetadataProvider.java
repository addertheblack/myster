package com.myster.filemanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.myster.mml.MessagePak;
import org.junit.jupiter.api.Test;

class TestTypeResolvingMetadataProvider {
    @Test
    void routesByMetadataType() {
        AtomicInteger calls = new AtomicInteger();
        TypeResolvingMetadataProvider provider = new TypeResolvingMetadataProvider(
                Map.of(MetadataType.AUDIO, (messagePack, path) -> {
                    calls.incrementAndGet();
                    messagePack.putLong("/LengthSec", 12);
                }));
        MessagePak messagePack = MessagePak.newEmpty();

        provider.enrich(MetadataType.AUDIO, messagePack, Path.of("song.mp3"));

        assertEquals(1, calls.get());
        assertEquals(12L, messagePack.getLong("/LengthSec").orElseThrow());
    }

    @Test
    void unsupportedTypeNoOps() {
        TypeResolvingMetadataProvider provider = new TypeResolvingMetadataProvider(Map.of());
        MessagePak messagePack = MessagePak.newEmpty();

        provider.enrich(MetadataType.AUDIO, messagePack, Path.of("song.mp3"));

        assertTrue(messagePack.list("/").isEmpty());
    }
}

package com.myster.filemanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class TestDefaultMetadataTypeRegistry {
    private final DefaultMetadataTypeRegistry registry = new DefaultMetadataTypeRegistry();

    @Test
    void get_returnsAudioProfile() {
        assertSame(MetadataType.AUDIO, registry.get("audio"));
    }

    @Test
    void get_returnsImageProfile() {
        assertSame(MetadataType.IMAGE, registry.get("image"));
    }

    @Test
    void get_returnsVideoProfile() {
        assertSame(MetadataType.VIDEO, registry.get("video"));
    }

    @Test
    void get_normalizesAndFallsBackToGeneric() {
        assertSame(MetadataType.AUDIO, registry.get(" AUDIO "));
        assertSame(MetadataType.VIDEO, registry.get(" VIDEO "));
        assertSame(MetadataType.GENERIC, registry.get(null));
        assertSame(MetadataType.GENERIC, registry.get(" "));
        assertSame(MetadataType.GENERIC, registry.get("movie"));
    }

    @Test
    void supportedTypes_containsUniqueBuiltIns() {
        Set<String> ids = registry.supportedTypes().stream()
                .map(MetadataType::id)
                .collect(Collectors.toSet());

        assertEquals(registry.supportedTypes().size(), ids.size());
        assertTrue(ids.contains("generic"));
        assertTrue(ids.contains("audio"));
        assertTrue(ids.contains("image"));
        assertTrue(ids.contains("video"));
    }

    @Test
    void builtInsHaveInitialCacheVersion() {
        for (MetadataType type : registry.supportedTypes()) {
            assertEquals(1, type.cacheVersion(), type.id());
        }
    }
}

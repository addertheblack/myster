package com.myster.filemanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;

import com.myster.type.MetadataTypeId;
import org.junit.jupiter.api.Test;

class TestDefaultMetadataTypeRegistry {
    private final DefaultMetadataTypeRegistry registry = new DefaultMetadataTypeRegistry();

    @Test
    void get_returnsAudioProfile() {
        assertSame(MetadataType.AUDIO, registry.get(MetadataTypeId.AUDIO));
    }

    @Test
    void get_returnsImageProfile() {
        assertSame(MetadataType.IMAGE, registry.get(MetadataTypeId.IMAGE));
    }

    @Test
    void get_returnsVideoProfile() {
        assertSame(MetadataType.VIDEO, registry.get(MetadataTypeId.VIDEO));
    }

    @Test
    void get_fallsBackToGenericForUnknownTypedId() {
        assertSame(MetadataType.GENERIC, registry.get(MetadataTypeId.fromString("movie")));
    }

    @Test
    void supportedTypes_containsUniqueBuiltIns() {
        Set<MetadataTypeId> ids = registry.supportedTypes().stream()
                .map(MetadataType::id)
                .collect(Collectors.toSet());

        assertEquals(registry.supportedTypes().size(), ids.size());
        assertTrue(ids.contains(MetadataTypeId.GENERIC));
        assertTrue(ids.contains(MetadataTypeId.AUDIO));
        assertTrue(ids.contains(MetadataTypeId.IMAGE));
        assertTrue(ids.contains(MetadataTypeId.VIDEO));
    }

    @Test
    void builtInsHaveInitialCacheVersion() {
        for (MetadataType type : registry.supportedTypes()) {
            assertEquals(1, type.cacheVersion(), type.id().getIdentifier());
        }
    }
}

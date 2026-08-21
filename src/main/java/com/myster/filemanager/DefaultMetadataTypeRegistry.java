package com.myster.filemanager;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Built-in metadata profile registry.
 */
public class DefaultMetadataTypeRegistry implements MetadataTypeRegistry {
    private final Map<String, MetadataType> types;

    public DefaultMetadataTypeRegistry() {
        Map<String, MetadataType> mutableTypes = new LinkedHashMap<>();
        mutableTypes.put(MetadataType.GENERIC.id(), MetadataType.GENERIC);
        mutableTypes.put(MetadataType.AUDIO.id(), MetadataType.AUDIO);
        mutableTypes.put(MetadataType.IMAGE.id(), MetadataType.IMAGE);
        mutableTypes.put(MetadataType.VIDEO.id(), MetadataType.VIDEO);
        types = Map.copyOf(mutableTypes);
    }

    @Override
    public MetadataType get(String metadataTypeId) {
        String normalizedId = normalize(metadataTypeId);
        if (normalizedId.isEmpty()) {
            return generic();
        }
        return types.getOrDefault(normalizedId, generic());
    }

    @Override
    public MetadataType generic() {
        return MetadataType.GENERIC;
    }

    @Override
    public Collection<MetadataType> supportedTypes() {
        return types.values();
    }

    static String normalize(String metadataTypeId) {
        if (metadataTypeId == null) {
            return "";
        }
        return metadataTypeId.trim().toLowerCase(Locale.ROOT);
    }
}

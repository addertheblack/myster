package com.myster.filemanager;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import com.myster.type.MetadataTypeId;

/**
 * Built-in metadata profile registry.
 */
public class DefaultMetadataTypeRegistry implements MetadataTypeRegistry {
    private final Map<MetadataTypeId, MetadataType> types;

    public DefaultMetadataTypeRegistry() {
        Map<MetadataTypeId, MetadataType> mutableTypes = new LinkedHashMap<>();
        mutableTypes.put(MetadataType.GENERIC.id(), MetadataType.GENERIC);
        mutableTypes.put(MetadataType.AUDIO.id(), MetadataType.AUDIO);
        mutableTypes.put(MetadataType.IMAGE.id(), MetadataType.IMAGE);
        mutableTypes.put(MetadataType.VIDEO.id(), MetadataType.VIDEO);
        types = Map.copyOf(mutableTypes);
    }

    @Override
    public MetadataType get(MetadataTypeId metadataTypeId) {
        return types.getOrDefault(metadataTypeId, generic());
    }

    @Override
    public MetadataType generic() {
        return MetadataType.GENERIC;
    }

    @Override
    public Collection<MetadataType> supportedTypes() {
        return types.values();
    }
}

package com.myster.filemanager;

import java.util.Collection;

/**
 * Registry of metadata profiles keyed by stable metadata type id.
 */
public interface MetadataTypeRegistry {
    MetadataType get(String metadataTypeId);

    MetadataType generic();

    Collection<MetadataType> supportedTypes();
}

package com.myster.filemanager;

import java.nio.file.Path;

import com.myster.mml.MessagePak;

/**
 * Metadata provider used by tests and fallback paths when no enrichment is needed.
 */
public class NoOpMetadataProvider implements MetadataProvider {
    @Override
    public void enrich(MetadataType metadataType, MessagePak messagePack, Path path) {
        // no metadata to add
    }
}

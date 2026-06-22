package com.myster.filemanager;

import java.nio.file.Path;

import com.myster.mml.MessagePak;

/**
 * Enriches metadata for one specific {@link MetadataType}.
 */
public interface TypedMetadataProvider {
    void enrich(MessagePak messagePack, Path path);
}

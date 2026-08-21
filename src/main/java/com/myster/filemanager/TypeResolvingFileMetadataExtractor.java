package com.myster.filemanager;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

import com.myster.mml.MessagePak;

/**
 * Routes metadata requests to the typed extractor registered for the requested
 * {@link MetadataType}.
 */
public class TypeResolvingFileMetadataExtractor implements FileMetadataExtractor {
    private final Map<MetadataType, TypedMetadataExtractor> extractors;

    public TypeResolvingFileMetadataExtractor(Map<MetadataType, TypedMetadataExtractor> extractors) {
        this.extractors = Map.copyOf(Objects.requireNonNull(extractors));
    }

    @Override
    public void enrich(MetadataType metadataType, MessagePak messagePack, Path path) {
        TypedMetadataExtractor extractor = extractors.get(metadataType);
        if (extractor == null) {
            return;
        }

        extractor.enrich(messagePack, path);
    }
}

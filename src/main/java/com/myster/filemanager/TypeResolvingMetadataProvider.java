package com.myster.filemanager;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

import com.myster.mml.MessagePak;

/**
 * Routes metadata requests to the typed provider registered for the requested
 * {@link MetadataType}.
 */
public class TypeResolvingMetadataProvider implements MetadataProvider {
    private final Map<MetadataType, TypedMetadataProvider> providers;

    public TypeResolvingMetadataProvider(Map<MetadataType, TypedMetadataProvider> providers) {
        this.providers = Map.copyOf(Objects.requireNonNull(providers));
    }

    @Override
    public void enrich(MetadataType metadataType, MessagePak messagePack, Path path) {
        TypedMetadataProvider provider = providers.get(metadataType);
        if (provider == null) {
            return;
        }

        provider.enrich(messagePack, path);
    }
}

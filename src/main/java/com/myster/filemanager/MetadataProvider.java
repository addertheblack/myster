package com.myster.filemanager;

import java.nio.file.Path;

import com.myster.mml.MessagePak;

/**
 * Enriches a file's metadata payload with the requested metadata type.
 * <p>
 * Implementations modify {@code messagePack} in place. Ordinary parse failures
 * should be logged and skipped rather than thrown to indexing callers.
 * Providers that participate in persistent caching require the caller to pass a
 * base {@link FileItem} payload that already contains {@code /size}.
 */
public interface MetadataProvider {
    void enrich(MetadataType metadataType, MessagePak messagePack, Path path);
}

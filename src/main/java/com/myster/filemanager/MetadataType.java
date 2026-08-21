package com.myster.filemanager;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import com.myster.search.ui.FileTypeColumnHandler;
import com.myster.type.TypeDescriptionList;

/**
 * Runtime profile for a type of file metadata that Myster knows how to extract and display.
 * <p>
 * A concrete network {@link com.myster.type.MysterType} subscribes to one metadata type by storing
 * this profile's stable {@link #id()} in its {@link com.myster.type.TypeDescription}. The cache key
 * is a separate on-disk schema namespace. Changing the cache key intentionally invalidates old
 * cached entries for that metadata type without changing type subscriptions.
 */
public interface MetadataType {
    MetadataType GENERIC = BuiltInMetadataType.GENERIC;
    MetadataType AUDIO = BuiltInMetadataType.AUDIO;
    MetadataType IMAGE = BuiltInMetadataType.IMAGE;

    /**
     * Returns the stable lowercase id stored by {@link com.myster.type.TypeDescription}.
     */
    String id();

    /**
     * Returns the stable string used to namespace persistent cache entries.
     */
    String cacheKey();

    /**
     * Returns the MessagePak root keys that may be persisted for this metadata type.
     */
    List<String> cacheableKeys();

    /**
     * Returns the GUI column handler for this metadata type.
     */
    FileTypeColumnHandler getHandler(TypeDescriptionList tdList);

    /**
     * Creates the server-side file item used for files with this metadata type.
     */
    FileItem createFileItem(Path root, Path path, FileMetadataExtractor metadataExtractor);

    /**
     * Returns the typed extractor used to fill this profile's cacheable keys, if any.
     */
    Optional<TypedMetadataExtractor> typedMetadataExtractor();
}

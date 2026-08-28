package com.myster.filemanager;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import com.myster.search.ui.FileTypeColumnHandler;
import com.myster.type.MetadataTypeId;
import com.myster.type.TypeDescriptionList;

/**
 * Runtime profile for a type of file metadata that Myster knows how to extract and display.
 * <p>
 * A concrete network {@link com.myster.type.MysterType} subscribes to one metadata type by storing
 * this profile's stable {@link #id()} in its {@link com.myster.type.TypeDescription}. The
 * {@link #cacheVersion()} separately identifies the metadata representation stored in the local
 * cache, so changing cached fields does not change type subscriptions or cache entry identity.
 */
public interface MetadataType {
    MetadataType GENERIC = BuiltInMetadataType.GENERIC;
    MetadataType AUDIO = BuiltInMetadataType.AUDIO;
    MetadataType IMAGE = BuiltInMetadataType.IMAGE;
    MetadataType VIDEO = BuiltInMetadataType.VIDEO;

    /**
     * Returns the stable serialized identity for this runtime profile.
     */
    MetadataTypeId id();

    /**
     * Returns the positive version of this profile's cached metadata representation.
     * <p>
     * Increment this value whenever old positive or negative cache entries may be incomplete for
     * the current extractor, including when adding a newly cacheable field. A version mismatch is
     * treated as a cache miss and causes normal metadata extraction to replace the old entry.
     */
    int cacheVersion();

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

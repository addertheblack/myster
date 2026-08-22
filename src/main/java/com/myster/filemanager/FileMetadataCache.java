package com.myster.filemanager;

import java.util.Optional;

import com.myster.mml.MessagePak;

/**
 * Persistent cache for type-specific file metadata.
 * <p>
 * Values returned by this cache contain only metadata owned by the requested
 * {@link MetadataType}; generic file-list fields such as {@code /size},
 * {@code /path}, and hashes remain live values from {@link FileItem}.
 * A present, empty {@link MessagePak} is a negative cache hit indicating that extraction
 * completed without producing type-specific metadata. It must remain distinct from an empty
 * {@link Optional}, which represents a cache miss.
 * Implementations should treat missing, unreadable, or corrupt cache storage as
 * a cache miss instead of throwing to indexing callers.
 */
public interface FileMetadataCache {
    Optional<MessagePak> get(FileMetadataCacheKey key);

    void put(FileMetadataCacheKey key, MessagePak metadata);

    void remove(FileMetadataCacheKey key);
}

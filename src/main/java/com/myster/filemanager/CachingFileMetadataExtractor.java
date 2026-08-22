package com.myster.filemanager;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

import com.myster.mml.MessagePak;

/**
 * Top-level metadata extractor that adds persistent cache hit/miss behaviour.
 * <p>
 * The delegate receives the same {@link MessagePak} passed by the caller so it
 * can use generic fields such as {@code /size}. Only keys owned by the requested
 * {@link MetadataType} are persisted to disk.
 * Empty extraction results are persisted as negative cache entries so unsupported
 * files are not repeatedly scanned. They are invalidated by the same file identity,
 * metadata schema, and expiry rules as populated entries.
 * <p>
 * If the cache key cannot be built, enrichment is skipped. The delegate is only
 * called after a successful cache lookup miss so this extractor does not silently
 * bypass the persistent cache.
 *
 * @throws IllegalStateException if the supplied payload does not contain
 *         {@code /size}; callers must enrich the base {@link FileItem} payload
 *         rather than an empty metadata-only structure.
 */
public class CachingFileMetadataExtractor implements FileMetadataExtractor {
    private static final Logger log = Logger.getLogger(CachingFileMetadataExtractor.class.getName());

    private final FileMetadataCache cache;
    private final FileMetadataExtractor delegate;

    public CachingFileMetadataExtractor(FileMetadataCache cache, FileMetadataExtractor delegate) {
        this.cache = Objects.requireNonNull(cache);
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    public void enrich(MetadataType metadataType, MessagePak messagePack, Path path) {
        Optional<Long> fileSize = messagePack.getLong("/size");
        if (fileSize.isEmpty()) {
            throw new IllegalStateException("Metadata cache requires /size before enrichment: "
                    + path);
        }

        FileMetadataCacheKey key;
        try {
            key = FileMetadataCacheKey.from(metadataType, path, fileSize.get());
        } catch (IOException ex) {
            log.warning("Could not build metadata cache key for: " + path + " - "
                    + ex.getMessage());
            return;
        }

        Optional<MessagePak> cachedMetadata = cache.get(key);
        if (cachedMetadata.isPresent()) {
            MessagePakTreeUtils.copyAllowedKeys(cachedMetadata.get(), messagePack, metadataType);
            return;
        }

        delegate.enrich(metadataType, messagePack, path);

        MessagePak metadataOnly = MessagePakTreeUtils.copyAllowedKeys(messagePack, metadataType);
        cache.put(key, metadataOnly);
    }
}

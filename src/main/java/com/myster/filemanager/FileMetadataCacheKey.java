package com.myster.filemanager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Identity for one cached metadata payload.
 * <p>
 * The entry key and shard are derived from stable metadata type id plus normalized absolute path.
 * The type's cache version is carried separately and validated on read, so incrementing it causes
 * a cache miss without moving the entry. Size and last-modified milliseconds are also validated so
 * changed files miss the cache.
 */
public final class FileMetadataCacheKey {
    private final String metadataTypeId;
    private final int cacheVersion;
    private final String normalizedAbsolutePath;
    private final long size;
    private final long lastModifiedMillis;
    private final String entryKey;
    private final String shardId;

    private FileMetadataCacheKey(String metadataTypeId,
                                 int cacheVersion,
                                 String normalizedAbsolutePath,
                                 long size,
                                 long lastModifiedMillis,
                                 String entryKey) {
        this.metadataTypeId = Objects.requireNonNull(metadataTypeId);
        if (cacheVersion <= 0) {
            throw new IllegalArgumentException("cacheVersion must be positive");
        }
        this.cacheVersion = cacheVersion;
        this.normalizedAbsolutePath = Objects.requireNonNull(normalizedAbsolutePath);
        this.size = size;
        this.lastModifiedMillis = lastModifiedMillis;
        this.entryKey = Objects.requireNonNull(entryKey);
        this.shardId = entryKey.substring(0, 2);
    }

    public static FileMetadataCacheKey from(MetadataType metadataType, Path path, long fileSize)
            throws IOException {
        MetadataType type = Objects.requireNonNull(metadataType);
        return from(type.id().getIdentifier(), type.cacheVersion(), path, fileSize);
    }

    static FileMetadataCacheKey from(String metadataTypeId,
                                     int cacheVersion,
                                     Path path,
                                     long fileSize)
            throws IOException {
        Objects.requireNonNull(metadataTypeId);
        if (metadataTypeId.isBlank()) {
            throw new IllegalArgumentException("metadataTypeId must not be blank");
        }
        if (cacheVersion <= 0) {
            throw new IllegalArgumentException("cacheVersion must be positive");
        }
        String normalizedPath = path.toAbsolutePath().normalize().toString();

        // Getting the modified time is the slowest step in the process of doing a cache lookup
        // because we need to hit the file system.
        long lastModifiedMillis = Files.getLastModifiedTime(path).toMillis();
        String entryKey = sha256Hex(metadataTypeId + "\n" + normalizedPath);
        return new FileMetadataCacheKey(metadataTypeId, cacheVersion, normalizedPath, fileSize,
                lastModifiedMillis, entryKey);
    }

    public String metadataTypeId() {
        return metadataTypeId;
    }

    public int cacheVersion() {
        return cacheVersion;
    }

    public String normalizedAbsolutePath() {
        return normalizedAbsolutePath;
    }

    public long size() {
        return size;
    }

    public long lastModifiedMillis() {
        return lastModifiedMillis;
    }

    public String entryKey() {
        return entryKey;
    }

    public String shardId() {
        return shardId;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FileMetadataCacheKey other)) {
            return false;
        }

        return size == other.size
                && lastModifiedMillis == other.lastModifiedMillis
                && cacheVersion == other.cacheVersion
                && metadataTypeId.equals(other.metadataTypeId)
                && normalizedAbsolutePath.equals(other.normalizedAbsolutePath)
                && entryKey.equals(other.entryKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(metadataTypeId, cacheVersion, normalizedAbsolutePath, size,
                lastModifiedMillis, entryKey);
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 should always be available", ex);
        }
    }
}

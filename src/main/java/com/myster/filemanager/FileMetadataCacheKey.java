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
 * The entry key and shard are derived from metadata type plus normalized absolute
 * path. Size and last-modified millis are stored in the entry and validated on
 * read so changed files miss the cache.
 */
public final class FileMetadataCacheKey {
    private final String metadataType;
    private final String normalizedAbsolutePath;
    private final long size;
    private final long lastModifiedMillis;
    private final String entryKey;
    private final String shardId;

    private FileMetadataCacheKey(String metadataType,
                                 String normalizedAbsolutePath,
                                 long size,
                                 long lastModifiedMillis,
                                 String entryKey) {
        this.metadataType = Objects.requireNonNull(metadataType);
        this.normalizedAbsolutePath = Objects.requireNonNull(normalizedAbsolutePath);
        this.size = size;
        this.lastModifiedMillis = lastModifiedMillis;
        this.entryKey = Objects.requireNonNull(entryKey);
        this.shardId = entryKey.substring(0, 2);
    }

    public static FileMetadataCacheKey from(MetadataType metadataType, Path path, long fileSize)
            throws IOException {
        return from(Objects.requireNonNull(metadataType).cacheKey(), path, fileSize);
    }

    private static FileMetadataCacheKey from(String metadataType, Path path, long fileSize)
            throws IOException {
        String normalizedPath = path.toAbsolutePath().normalize().toString();

        // Getting the modified time is the slowest step in the process of doing a cache lookup
        // because we need to hit the file system.
        long lastModifiedMillis = Files.getLastModifiedTime(path).toMillis();
        String entryKey = sha256Hex(metadataType + "\n" + normalizedPath);
        return new FileMetadataCacheKey(metadataType, normalizedPath, fileSize, lastModifiedMillis,
                entryKey);
    }

    public String metadataType() {
        return metadataType;
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
                && metadataType.equals(other.metadataType)
                && normalizedAbsolutePath.equals(other.normalizedAbsolutePath)
                && entryKey.equals(other.entryKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(metadataType, normalizedAbsolutePath, size, lastModifiedMillis, entryKey);
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

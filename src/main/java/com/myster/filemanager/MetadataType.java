package com.myster.filemanager;

import java.util.List;

/**
 * Identifies a group of cacheable file metadata.
 * <p>
 * The cache key is a stable on-disk namespace. Changing the cache key intentionally
 * invalidates old cached entries for that metadata type.
 */
public enum MetadataType {
    AUDIO("audio-v1",
            List.of("/BitRate", "/Hz", "/LengthSec", "/ID3Name", "/Artist", "/Album"));

    private final String cacheKey;
    private final List<String> cacheableKeys;

    MetadataType(String cacheKey, List<String> cacheableKeys) {
        this.cacheKey = cacheKey;
        this.cacheableKeys = List.copyOf(cacheableKeys);
    }

    /**
     * Returns the stable string used to namespace persistent cache entries.
     */
    public String cacheKey() {
        return cacheKey;
    }

    /**
     * Returns the MessagePak root keys that may be persisted for this metadata type.
     */
    List<String> cacheableKeys() {
        return cacheableKeys;
    }
}

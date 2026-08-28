package com.myster.type;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import com.general.util.TypeSafeEnum;

/**
 * Stable, extensible identifier for a file metadata profile.
 *
 * <p>Known identifiers are canonical singleton constants. A valid identifier introduced by a
 * newer Myster version is represented by a non-canonical instance that preserves its identity,
 * allowing access lists to round-trip values this version cannot execute. Runtime implementation
 * lookup is handled separately by {@link com.myster.filemanager.MetadataTypeRegistry}.
 *
 * <p>Identifiers are lowercase ASCII tokens of at most 64 characters. They begin with a letter
 * and may additionally contain digits, dots, underscores, and hyphens. User interfaces must use
 * {@link #getDisplayName()} rather than exposing the serialized identifier directly.
 *
 * @see TypeSafeEnum
 */
public final class MetadataTypeId extends TypeSafeEnum<MetadataTypeId> {
    private static final int MAX_IDENTIFIER_LENGTH = 64;
    private static final Pattern VALID_IDENTIFIER =
            Pattern.compile("[a-z][a-z0-9._-]{0," + (MAX_IDENTIFIER_LENGTH - 1) + "}");

    public static final MetadataTypeId GENERIC =
            new MetadataTypeId("generic", "Generic");
    public static final MetadataTypeId AUDIO =
            new MetadataTypeId("audio", "Audio");
    public static final MetadataTypeId IMAGE =
            new MetadataTypeId("image", "Image");
    public static final MetadataTypeId VIDEO =
            new MetadataTypeId("video", "Video");

    private static final Map<String, MetadataTypeId> KNOWN_TYPES =
            canonicalValueMap(GENERIC, AUDIO, IMAGE, VIDEO);

    private final String displayName;

    private MetadataTypeId(String identifier, String displayName) {
        super(identifier);
        this.displayName = displayName;
    }

    private MetadataTypeId(String identifier, String displayName, boolean canonical) {
        super(identifier, canonical);
        this.displayName = displayName;
    }

    /**
     * Returns the known constant for an identifier or a value preserving a future identifier.
     *
     * @param identifier serialized metadata type identifier
     * @return canonical known value or a distinct non-canonical value
     * @throws IllegalArgumentException if the identifier is null, blank, too long, or malformed
     */
    public static MetadataTypeId fromString(String identifier) {
        String normalized = normalize(identifier);
        return from(normalized, KNOWN_TYPES,
                id -> new MetadataTypeId(id, unknownDisplayName(id), false));
    }

    /** Returns a safe, human-readable label suitable for Swing controls. */
    public String getDisplayName() {
        return displayName;
    }

    private static String normalize(String identifier) {
        if (identifier == null) {
            throw new IllegalArgumentException("Metadata type identifier cannot be null");
        }
        String normalized = identifier.trim().toLowerCase(Locale.ROOT);
        if (!VALID_IDENTIFIER.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid metadata type identifier: " + identifier);
        }
        return normalized;
    }

    private static String unknownDisplayName(String identifier) {
        String[] words = identifier.split("[._-]+");
        StringBuilder readable = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (!readable.isEmpty()) {
                readable.append(' ');
            }
            readable.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                readable.append(word.substring(1));
            }
        }
        return "Unknown metadata type — " + readable;
    }
}

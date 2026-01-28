package com.myster.type;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.prefs.Preferences;

/**
 * Represents a user-created custom type definition with all configurable properties.
 *
 * <p>This class encapsulates the metadata for a custom MysterType including:
 * <ul>
 *   <li>User-readable name and description</li>
 *   <li>The public key that uniquely identifies this type</li>
 *   <li>File extensions to filter by</li>
 *   <li>Whether to search inside archive files (ZIP, etc.)</li>
 *   <li>Whether the type is public or private (private not yet implemented)</li>
 * </ul>
 *
 * <p>Instances can be serialized to/from Java Preferences for persistence.
 */
public class CustomTypeDefinition {
    private final PublicKey publicKey;
    private final String name;
    private final String description;
    private final String[] extensions;
    private final boolean searchInArchives;
    private final boolean isPublic;

    // Preference keys
    private static final String KEY_NAME = "name";
    private static final String KEY_DESCRIPTION = "description";
    private static final String KEY_PUBLIC_KEY = "publicKey";
    private static final String KEY_EXTENSIONS = "extensions";
    private static final String KEY_SEARCH_IN_ARCHIVES = "searchInArchives";
    private static final String KEY_IS_PUBLIC = "isPublic";

    /**
     * Creates a new custom type definition.
     *
     * @param publicKey the public key that defines this type (must not be null)
     * @param name user-readable name for this type (must not be null or empty)
     * @param description user-readable description (may be null or empty)
     * @param extensions file extensions to filter by (must not be null, may be empty)
     * @param searchInArchives whether to search inside ZIP and other archive files
     * @param isPublic whether this is a public network (private not yet implemented)
     * @throws IllegalArgumentException if publicKey is null, name is null/empty, or extensions is null
     */
    public CustomTypeDefinition(PublicKey publicKey, String name, String description,
                                 String[] extensions, boolean searchInArchives, boolean isPublic) {
        if (publicKey == null) {
            throw new IllegalArgumentException("Public key cannot be null");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Name cannot be null or empty");
        }
        if (extensions == null) {
            throw new IllegalArgumentException("Extensions cannot be null");
        }

        this.publicKey = publicKey;
        this.name = name.trim();
        this.description = (description == null || description.trim().isEmpty()) ? "" : description.trim();
        this.extensions = extensions.clone();
        this.searchInArchives = searchInArchives;
        this.isPublic = isPublic;
    }

    /**
     * Generates a new custom type definition with a freshly generated key pair.
     *
     * @param name user-readable name for this type
     * @param description user-readable description
     * @param extensions file extensions to filter by
     * @param searchInArchives whether to search inside archive files
     * @param isPublic whether this is a public network
     * @return a new CustomTypeDefinition with a generated public key
     * @throws IllegalStateException if key generation fails
     */
    public static CustomTypeDefinition generateNew(String name, String description,
                                                   String[] extensions, boolean searchInArchives, boolean isPublic) {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            KeyPair keyPair = keyGen.generateKeyPair();

            return new CustomTypeDefinition(keyPair.getPublic(), name, description,
                                             extensions, searchInArchives, isPublic);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA algorithm not available", e);
        }
    }

    /**
     * Creates a MysterType instance for this custom type definition.
     *
     * @return a MysterType based on this definition's public key
     */
    public MysterType toMysterType() {
        return new MysterType(publicKey);
    }

    /**
     * Saves this custom type definition to a Preferences node.
     *
     * @param prefs the Preferences node to save to
     */
    public void toPreferences(Preferences prefs) {
        prefs.put(KEY_NAME, name);
        prefs.put(KEY_DESCRIPTION, description);
        prefs.put(KEY_PUBLIC_KEY, Base64.getEncoder().encodeToString(publicKey.getEncoded()));
        prefs.put(KEY_EXTENSIONS, String.join(",", extensions));
        prefs.putBoolean(KEY_SEARCH_IN_ARCHIVES, searchInArchives);
        prefs.putBoolean(KEY_IS_PUBLIC, isPublic);
    }

    /**
     * Loads a custom type definition from a Preferences node.
     *
     * @param prefs the Preferences node to load from
     * @return the loaded CustomTypeDefinition
     * @throws IllegalStateException if the data is corrupted or invalid
     */
    public static CustomTypeDefinition fromPreferences(Preferences prefs) {
        try {
            String name = prefs.get(KEY_NAME, null);
            String description = prefs.get(KEY_DESCRIPTION, "");
            String publicKeyBase64 = prefs.get(KEY_PUBLIC_KEY, null);
            String extensionsStr = prefs.get(KEY_EXTENSIONS, "");
            boolean searchInArchives = prefs.getBoolean(KEY_SEARCH_IN_ARCHIVES, false);
            boolean isPublic = prefs.getBoolean(KEY_IS_PUBLIC, true);

            if (name == null || publicKeyBase64 == null) {
                throw new IllegalStateException("Missing required fields in preferences");
            }

            // Decode public key
            byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyBase64);
            PublicKey publicKey = java.security.KeyFactory.getInstance("RSA")
                    .generatePublic(new java.security.spec.X509EncodedKeySpec(publicKeyBytes));

            // Parse extensions
            String[] extensions = extensionsStr.isEmpty() ? new String[0] : extensionsStr.split(",");

            return new CustomTypeDefinition(publicKey, name, description, extensions,
                                             searchInArchives, isPublic);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load custom type from preferences", e);
        }
    }

    /**
     * Validates this custom type definition.
     *
     * @return true if valid, false otherwise
     */
    public boolean isValid() {
        return publicKey != null &&
               name != null && !name.trim().isEmpty() &&
               extensions != null;
    }

    // Getters

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String[] getExtensions() {
        return extensions.clone();
    }

    public boolean isSearchInArchives() {
        return searchInArchives;
    }

    public boolean isPublic() {
        return isPublic;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CustomTypeDefinition that = (CustomTypeDefinition) o;
        return searchInArchives == that.searchInArchives &&
               isPublic == that.isPublic &&
               Objects.equals(publicKey, that.publicKey) &&
               Objects.equals(name, that.name) &&
               Objects.equals(description, that.description) &&
               Arrays.equals(extensions, that.extensions);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(publicKey, name, description, searchInArchives, isPublic);
        result = 31 * result + Arrays.hashCode(extensions);
        return result;
    }

    @Override
    public String toString() {
        return "CustomTypeDefinition{" +
               "name='" + name + '\'' +
               ", extensions=" + Arrays.toString(extensions) +
               ", searchInArchives=" + searchInArchives +
               ", isPublic=" + isPublic +
               '}';
    }
}


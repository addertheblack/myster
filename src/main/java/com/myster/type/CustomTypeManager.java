package com.myster.type;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Manages persistence of custom type definitions using Java Preferences API.
 *
 * <p>Custom types are stored under the "CustomTypes" preferences node, with each
 * type stored in a child node named by its {@link MysterType#toHexString()} value.
 * This provides a compact, unique identifier for each custom type.
 *
 * <p>Example structure:
 * <pre>
 * CustomTypes/
 *   a3f5c2d1.../  (hex string from MysterType.toShortBytes())
 *     name = "My Custom Network"
 *     description = "Files for my project"
 *     publicKey = &lt;base64&gt;
 *     extensions = "doc,pdf,txt"
 *     searchInArchives = true
 *     isPublic = true
 * </pre>
 */
public class CustomTypeManager {
    private static final Logger log = Logger.getLogger(CustomTypeManager.class.getName());
    private static final String CUSTOM_TYPES_NODE = "CustomTypes";

    private final Preferences prefs;

    /**
     * Creates a new CustomTypeManager.
     *
     * @param prefs the root Preferences node to use for storage
     */
    public CustomTypeManager(Preferences prefs) {
        this.prefs = prefs;
    }

    /**
     * Loads all custom type definitions from preferences.
     *
     * @return list of all custom type definitions (may be empty, never null)
     */
    public List<CustomTypeDefinition> loadCustomTypes() {
        List<CustomTypeDefinition> customTypes = new ArrayList<>();

        try {
            Preferences customTypesRoot = prefs.node(CUSTOM_TYPES_NODE);
            String[] typeNodeNames = customTypesRoot.childrenNames();

            for (String nodeName : typeNodeNames) {
                try {
                    Preferences typeNode = customTypesRoot.node(nodeName);
                    CustomTypeDefinition def = CustomTypeDefinition.fromPreferences(typeNode);
                    customTypes.add(def);
                } catch (Exception e) {
                    log.warning("Failed to load custom type from node " + nodeName + ": " + e.getMessage());
                    // Continue loading other types even if one fails
                }
            }

            log.info("Loaded " + customTypes.size() + " custom type(s)");
        } catch (BackingStoreException e) {
            log.severe("Failed to load custom types: " + e.getMessage());
        }

        return customTypes;
    }

    /**
     * Saves a custom type definition to preferences.
     * Creates a new entry if the type doesn't exist, updates if it does.
     *
     * @param def the custom type definition to save
     */
    public void saveCustomType(CustomTypeDefinition def) {
        try {
            MysterType type = def.toMysterType();
            String nodeName = type.toHexString();

            Preferences customTypesRoot = prefs.node(CUSTOM_TYPES_NODE);
            Preferences typeNode = customTypesRoot.node(nodeName);

            def.toPreferences(typeNode);
            typeNode.flush();

            log.info("Saved custom type: " + def.getName() + " (node: " + nodeName + ")");
        } catch (BackingStoreException e) {
            log.severe("Failed to save custom type " + def.getName() + ": " + e.getMessage());
            throw new IllegalStateException("Failed to save custom type", e);
        }
    }

    /**
     * Deletes a custom type from preferences.
     *
     * @param type the MysterType to delete
     * @throws IllegalArgumentException if the type doesn't exist
     */
    public void deleteCustomType(MysterType type) {
        try {
            String nodeName = type.toHexString();
            Preferences customTypesRoot = prefs.node(CUSTOM_TYPES_NODE);

            if (!customTypesRoot.nodeExists(nodeName)) {
                throw new IllegalArgumentException("Custom type does not exist: " + type);
            }

            Preferences typeNode = customTypesRoot.node(nodeName);
            typeNode.removeNode();
            customTypesRoot.flush();

            log.info("Deleted custom type (node: " + nodeName + ")");
        } catch (BackingStoreException e) {
            log.severe("Failed to delete custom type " + type + ": " + e.getMessage());
            throw new IllegalStateException("Failed to delete custom type", e);
        }
    }

    /**
     * Updates an existing custom type definition.
     * This is equivalent to saving - it overwrites the existing entry.
     *
     * @param type the MysterType to update
     * @param def the new custom type definition
     */
    public void updateCustomType(MysterType type, CustomTypeDefinition def) {
        // Verify the type matches
        if (!type.equals(def.toMysterType())) {
            throw new IllegalArgumentException("Type mismatch: cannot change public key during update");
        }

        saveCustomType(def);
    }

    /**
     * Checks if a custom type exists in preferences.
     *
     * @param type the MysterType to check
     * @return true if the type exists, false otherwise
     */
    public boolean exists(MysterType type) {
        try {
            String nodeName = type.toHexString();
            Preferences customTypesRoot = prefs.node(CUSTOM_TYPES_NODE);
            return customTypesRoot.nodeExists(nodeName);
        } catch (BackingStoreException e) {
            log.warning("Failed to check if custom type exists: " + e.getMessage());
            return false;
        }
    }
}


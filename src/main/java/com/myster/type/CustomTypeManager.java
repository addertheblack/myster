package com.myster.type;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Persists the enabled/disabled state of custom types using the Java Preferences API.
 *
 * <p>This class is intentionally minimal: it stores only whether each custom type exists and
 * whether it is enabled. All richer metadata (name, description, extensions, public key, etc.)
 * lives in the type's {@link com.myster.access.AccessList} file on disk and is managed by
 * {@link com.myster.access.AccessListManager}.
 *
 * <p>Prefs node structure:
 * <pre>
 * CustomTypes/
 *   {mysterType_hex}/   ← node name is the hex representation of the MysterType
 *     enabled = true    ← the only key written
 * </pre>
 *
 * <p>Old nodes that contain extra keys (name, description, publicKey, etc.) from a previous
 * implementation are silently ignored on read. If the corresponding access list file is
 * missing, the caller is responsible for deleting the stale prefs node via
 * {@link #deleteCustomType(MysterType)}.
 */
public class CustomTypeManager {
    private static final Logger log = Logger.getLogger(CustomTypeManager.class.getName());
    private static final String CUSTOM_TYPES_NODE = "CustomTypes";
    private static final String KEY_ENABLED = "enabled";

    private final Preferences prefs;

    /**
     * Creates a new CustomTypeManager.
     *
     * @param prefs the root Preferences node under which the {@code CustomTypes} child is stored
     */
    public CustomTypeManager(Preferences prefs) {
        this.prefs = prefs;
    }

    /**
     * Saves the enabled/disabled state for a custom type. Creates the prefs node if absent.
     * Any other keys that may already exist in the node are left untouched.
     *
     * @param type the type to persist
     * @param enabled whether the type should be enabled
     */
    public void saveEnabled(MysterType type, boolean enabled) {
        try {
            Preferences typeNode = prefs.node(CUSTOM_TYPES_NODE).node(type.toHexString());
            typeNode.putBoolean(KEY_ENABLED, enabled);
            typeNode.flush();
            log.fine("Saved enabled=" + enabled + " for type: " + type.toHexString());
        } catch (BackingStoreException e) {
            log.severe("Failed to save enabled state for type " + type.toHexString() + ": " + e.getMessage());
            // no throw. Best effort only.
        }
    }

    /**
     * Loads the enabled/disabled state for all known custom types.
     *
     * <p>Node names that cannot be parsed as valid {@link MysterType} hex strings are silently
     * skipped and logged at WARNING level. Unknown prefs keys within a node are ignored.
     *
     * @return a map from {@link MysterType} to its enabled flag; never null, may be empty
     */
    public Map<MysterType, Boolean> loadEnabledTypes() {
        Map<MysterType, Boolean> result = new HashMap<>();
        try {
            Preferences customTypesRoot = prefs.node(CUSTOM_TYPES_NODE);
            String[] nodeNames = customTypesRoot.childrenNames();

            for (String nodeName : nodeNames) {
                try {
                    MysterType type = MysterType.fromHexString(nodeName);
                    Preferences typeNode = customTypesRoot.node(nodeName);
                    boolean enabled = typeNode.getBoolean(KEY_ENABLED, false);
                    result.put(type, enabled);
                } catch (IOException e) {
                    log.warning("Skipping unrecognised CustomTypes prefs node: " + nodeName);
                }
            }

            log.info("Loaded " + result.size() + " custom type enabled state(s)");
        } catch (BackingStoreException e) {
            log.severe("Failed to load custom type enabled states: " + e.getMessage());
        }
        return result;
    }

    /**
     * Deletes the prefs node for the given type. Used when removing a custom type or
     * cleaning up stale nodes that have no corresponding access list on disk.
     *
     * @param type the type to remove
     */
    public void deleteCustomType(MysterType type) {
        try {
            Preferences customTypesRoot = prefs.node(CUSTOM_TYPES_NODE);
            String nodeName = type.toHexString();
            if (customTypesRoot.nodeExists(nodeName)) {
                customTypesRoot.node(nodeName).removeNode();
                customTypesRoot.flush();
                log.info("Deleted prefs node for type: " + nodeName);
            }
        } catch (BackingStoreException e) {
            log.severe("Failed to delete prefs node for type " + type.toHexString() + ": " + e.getMessage());
            // no throws best effort only
        }
    }
}

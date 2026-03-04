package com.myster.type;

import java.io.IOException;
import java.util.Optional;

import com.myster.access.AccessList;

/**
 * The TypeDescriptionList contains some basic type information for most file
 * based types on the Myster network. The TypeDescriptionList is loaded from a
 * file called "TypeDescriptionList.mml"
 *
 *  Can be called from any thread.
 */
public interface TypeDescriptionList {
    MysterType getType(StandardTypes t);
    
    /**
     * Returns a TypeDescription for that MysterType or null if no
     * TypeDescription Exists
     */
    Optional<TypeDescription> get(MysterType type);

    /**
     * Returns all TypeDescriptionObjects known
     */
    TypeDescription[] getAllTypes();

    /**
     * Returns all enabled TypeDescriptionObjects known
     */
    TypeDescription[] getEnabledTypes();

    /**
     * Returns all TypeDescriptionObjects enabled
     */
    boolean isTypeEnabled(MysterType type);

    /**
     * Returns if the Type is enabled in the prefs (as opposed to if the type
     * was enabled as of the beginning of the last execution which is what the
     * "isTypeEnabled" functions does.
     */
    boolean isTypeEnabledInPrefs(MysterType type);

    /**
     * Adds a listener to be notified if there is a change in the enabled/
     * unenabled-ness of of type.
     */
    void addTypeListener(TypeListener l);

    /**
     * Adds a listener to be notified if there is a change in the enabled/
     * unenabled-ness of of type.
     */
    void removeTypeListener(TypeListener l);

    /**
     * Enabled / disabled a type. Note this value comes into effect next time
     * Myster is launched.
     */
    void setEnabledType(MysterType type, boolean enable);

    /**
     * Adds a custom type definition to this list.
     * The type will be persisted to preferences and available immediately.
     *
     * @param def the custom type definition to add
     * @throws IllegalArgumentException if a type with the same public key already exists
     */
    void addCustomType(CustomTypeDefinition def);

    /**
     * Removes a custom type from this list.
     * Only custom types can be removed; attempting to remove a default type will throw an exception.
     *
     * @param type the MysterType to remove
     * @throws IllegalArgumentException if the type doesn't exist or is a default type
     */
    void removeCustomType(MysterType type);

    /**
     * Updates an existing custom type definition.
     * Only custom types can be updated; attempting to update a default type will throw an exception.
     *
     * @param type the MysterType to update
     * @param def the new custom type definition (must have the same public key)
     * @throws IllegalArgumentException if the type doesn't exist, is a default type, or the public key doesn't match
     */
    void updateCustomType(MysterType type, CustomTypeDefinition def);

    /**
     * Gets the CustomTypeDefinition for a custom type.
     * Returns empty Optional for default types or types that don't exist.
     *
     * @param type the MysterType to look up
     * @return Optional containing the CustomTypeDefinition if this is a custom type, empty otherwise
     */
    Optional<CustomTypeDefinition> getCustomTypeDefinition(MysterType type);

    /**
     * Imports a type from a remotely-fetched access list, permanently adding it to the local
     * type registry as enabled.
     *
     * <p>Saves the access list to disk, enables the type in the prefs index, and fires a
     * {@code typeEnabled} event so all subscribers ({@code TypeManagerPreferences},
     * {@code FileTypeListManager}, {@code Tracker}) pick it up automatically.
     *
     * <p>Unlike {@link #addCustomType}, this method does not require an admin key — imported
     * types are read-only in the editor (the key-file gate in {@code TypeEditorPanel} applies).
     *
     * @param accessList the access list fetched from the remote server
     * @throws IllegalArgumentException if a type with the same identifier is already registered
     * @throws IllegalStateException    if the access list chain fails validation
     * @throws IOException              if saving the access list to disk fails
     */
    void importType(AccessList accessList) throws IOException;
}






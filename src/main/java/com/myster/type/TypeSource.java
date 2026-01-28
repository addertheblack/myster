package com.myster.type;

/**
 * Indicates the source of a type definition - whether it's a built-in default type
 * or a user-created custom type.
 *
 * <p>This distinction is important for UI purposes:
 * <ul>
 *   <li>{@link #DEFAULT} types are loaded from TypeDescriptionList.mml and cannot be edited or deleted</li>
 *   <li>{@link #CUSTOM} types are user-created, persisted in preferences, and can be edited or deleted</li>
 * </ul>
 */
public enum TypeSource {
    /**
     * Built-in types loaded from TypeDescriptionList.mml resource file.
     * These types cannot be edited or deleted by users.
     */
    DEFAULT,

    /**
     * User-created custom types persisted in preferences.
     * These types can be edited and deleted by users.
     */
    CUSTOM
}


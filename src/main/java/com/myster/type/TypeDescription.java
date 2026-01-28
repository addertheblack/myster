/*
 * 
 * Title: Myster Open Source Author: Andrew Trumper Description: Generic Myster
 * Code
 * 
 * This code is under GPL
 * 
 * Copyright Andrew Trumper 2000-2001
 */

package com.myster.type;

/**
 * This class represents a MysterType and the meta data associated with that
 * MysterType (ie: it describes the type). Usually this consists of the
 * MysterType itself, a description of that type (to appear in UI elements), a
 * list of file extension (to use to filter out irrelevant files), an
 * "isEnabledByDefault" flag and an "isArchived" flag to signal whether the file
 * manager should search inside .zip files and other archives for files with the
 * previously indicated extensions.
 * 
 * @see TypeDescriptionList where TypeDescription is used
 * @author Andrew Trumper
 */

public class TypeDescription {
    private final MysterType type;
    private final String description;
    private final String[] extensions;
    private final boolean isArchived;
    private final boolean isEnabledByDefault;
    private final String internalName;
    private final TypeSource source;

    public TypeDescription(MysterType type, String internalName, String description, String[] extensions,
            boolean isArchived, boolean isEnabledByDefault ) {
        this(type, internalName, description, extensions, isArchived, isEnabledByDefault, TypeSource.DEFAULT);
    }

    /**
     * Creates a TypeDescription object with an explicit source.
     *
     * @param type
     *            The MysterType this description is for.
     * @param internalName
     *            Internal name for this type (used for StandardTypes lookup)
     * @param description
     *            A short description of this type
     * @param extensions
     *            an array of file extensions to filter by. If this array is of
     *            size 0 then no files will be filtered.
     * @param isArchived
     *            a flag to indicate to the FileManager to look for this type of
     *            file inside .zip like archive files.
     * @param isEnabledByDefault
     *            a flag to indicate that this file type should be enabled by
     *            default
     * @param source
     *            whether this is a DEFAULT (built-in) or CUSTOM (user-created) type
     */
    public TypeDescription(MysterType type, String internalName, String description, String[] extensions,
            boolean isArchived, boolean isEnabledByDefault, TypeSource source) {
        this.type = type;
        this.internalName = internalName;
        this.description = description;
        this.extensions = extensions;
        this.isArchived = isArchived;
        this.isEnabledByDefault = isEnabledByDefault;
        this.source = source;
    }

    public String getTypeAsString() {
        return type.toString();
    }

    public MysterType getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    /**
     * If this array is of length = 0 then simply allow every file regardless of
     * extension
     */
    public String[] getExtensions() {
        return extensions.clone();
    }

    /**
     * Indicates if the file could be in an archive. An example would be a ROM
     * packaged inside a .zip file or an album of MP3s packaged in a .zip file.
     * MPG3 and ROMS file types have this flag set to true.
     * <p>
     * ON the other hand, the TEXT type has this flag set to false since almost
     * every package has a readme.txt..
     * 
     * @return true if file might be in an archive, false otherwise
     */
    public boolean isArchived() {
        return isArchived;
    }
    
    public String getInternalName() {
        return internalName;
    }

    /**
     * Indicates if this type should be enabled by default.
     * 
     * @return true if type should be enabled by default, false otherwise.
     */
    public boolean isEnabledByDefault() {
        return isEnabledByDefault;
    }

    /**
     * Gets the source of this type definition.
     *
     * @return TypeSource.DEFAULT for built-in types, TypeSource.CUSTOM for user-created types
     */
    public TypeSource getSource() {
        return source;
    }

    /**
     * Indicates whether this type can be deleted by the user.
     * Only custom types can be deleted; default types cannot.
     *
     * @return true if this type can be deleted, false otherwise
     */
    public boolean isDeletable() {
        return source == TypeSource.CUSTOM;
    }

    /**
     * Indicates whether this type can be edited by the user.
     * Only custom types can be edited; default types cannot.
     *
     * @return true if this type can be edited, false otherwise
     */
    public boolean isEditable() {
        return source == TypeSource.CUSTOM;
    }

    public String toString() {
        return description + " ("+ type + ")";
    }
}

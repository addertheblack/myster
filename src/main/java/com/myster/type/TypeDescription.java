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

    /**
     * Creates a TypeDescription object.
     * 
     * @param type
     *            The MysterType this description is for.
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
     */
    public TypeDescription(MysterType type, String description, String[] extensions,
            boolean isArchived, boolean isEnabledByDefault ) {
        this.type = type;
        this.description = description;
        this.extensions = extensions;
        this.isArchived = isArchived;
        this.isEnabledByDefault = isEnabledByDefault;
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

    /**
     * Indicates if this type should be enabled by default.
     * 
     * @return true if type should be enabled by default, false otherwise.
     */
    public boolean isEnabledByDefault() {
        return isEnabledByDefault;
    }

    public String toString() {
        return type + " " + description;
    }
}

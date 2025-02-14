package com.myster.type;

import java.util.Optional;

/**
 * The TypeDescriptionList contains some basic type information for most file
 * based types on the Myster network. The TypeDescriptionList is loaded from a
 * file called "TypeDescriptionList.mml"
 *  
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
}


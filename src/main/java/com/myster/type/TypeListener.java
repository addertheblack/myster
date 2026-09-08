package com.myster.type;

/**
 * Receives changes to a {@link TypeDescriptionList}.
 *
 * @author Andrew Trumper
 */
public interface TypeListener {
    void typeDisabled(TypeDescriptionEvent e);
    void typeEnabled(TypeDescriptionEvent e);

    /**
     * Called when a registered type's description changes without changing whether it is enabled.
     *
     * @param e identifies the type whose current description should be read from the list
     */
    default void typeUpdated(TypeDescriptionEvent e) {}
}

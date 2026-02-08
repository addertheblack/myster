package com.myster.type;

/**
 * Over-ride this type to listen for changes in a TypeDescriptionList
 * 
 * @author Andrew Trumper
 *
 */

public interface TypeListener {
    void typeDisabled(TypeDescriptionEvent e);
    void typeEnabled(TypeDescriptionEvent e);
}
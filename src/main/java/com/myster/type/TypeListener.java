package com.myster.type;

/**
 * Over-ride this type to listen for changes in a TypeDescriptionList
 * 
 * @author Andrew Trumper
 *
 */

public interface TypeListener {
    public void typeDisabled(TypeDescriptionEvent e);
    public void typeEnabled(TypeDescriptionEvent e);
}
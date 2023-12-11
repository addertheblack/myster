package com.myster.type;

import com.general.events.EventListener;
import com.general.events.GenericEvent;

/**
 * Over-ride this type to listen for changes in a TypeDescriptionList
 * 
 * @author Andrew Trumper
 *
 */

public abstract class TypeListener extends EventListener {
    public void fireEvent(GenericEvent e) {
        TypeDescriptionEvent event = (TypeDescriptionEvent) e;
        switch (event.getID()) {
        case TypeDescriptionEvent.DISABLE:
            typeDisabled(event);
            break;
        case TypeDescriptionEvent.ENABLE:
            typeEnabled(event);
            break;
        default:
            err();
            break;
        }
    }

    public abstract void typeDisabled(TypeDescriptionEvent e);

    public abstract void typeEnabled(TypeDescriptionEvent e);
}
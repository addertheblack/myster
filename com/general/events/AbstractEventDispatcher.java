package com.general.events;

import java.util.Vector;

/**
 * Basic Event dispatcher with abstract event firing mechanism.
 * <p>
 * This class contains good default implementations of most methods allowing the
 * sub-class to decide upon the basic event dispatching mechanism.
 * 
 * @see com.general.events.EventListener
 * @see com.general.events.GenericEvent
 * @see com.general.events.EventDispatcher
 */
public abstract class AbstractEventDispatcher implements EventDispatcher {
    /** Contains the event listeners for this dispatcher. */
    protected Vector listeners = new Vector(10, 10);

    public void addListener(EventListener listener) {
        listeners.addElement(listener);
    }

    public void removeListener(EventListener listener) {
        listeners.removeElement(listener);
    }

    public int getNumberOfListeners() {
        return listeners.size();
    }

    public abstract void fireEvent(GenericEvent event);
}
package com.general.events;

import java.util.Vector;

/**
 * Basic Event dispatcher with abstract event firing mechanism.
 * <p>
 * Generally there is one dispatcher per object firing events. A one dispatcher per event type
 * fired. This setup allows event listeners to be garbage collected when the object dies instead of
 * hanging around forever because the dispatcher lifespan is different then the object's lifespan.
 * 
 * @see com.general.events.EventListener
 * @see com.general.events.GenericEvent
 */
public abstract class EventDispatcher {
    /** Contains the event listeners for this dispatcher. */
    protected Vector listeners = new Vector(10, 10);

    /**
     * Adds a listener to this event dispatcher.
     * 
     * @param listener
     */
    public void addListener(EventListener listener) {
        listeners.addElement(listener);
    }

    /**
     * Removes a listener form this dispatcher.
     * 
     * @param listener
     */
    public void removeListener(EventListener listener) {
        listeners.removeElement(listener);
    }

    /**
     * Returns the number of listeners currently registered with this dispatcher.
     * 
     * @return the number of listeners that this dispatcher contains.
     */
    public int getNumberOfListeners() {
        return listeners.size();
    }

    /**
     * Dispatches an event to its listeners.
     * 
     * @param event
     *            to dispatch
     */
    public abstract void fireEvent(GenericEvent event);
}
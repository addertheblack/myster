package com.general.events;

/**
 * Generic event for use by the dispatcher framework.
 * 
 * @see com.general.events.EventDispatcher
 * @see com.general.events.EventListener
 */
public class GenericEvent {
    private final int id;

    /**
     * Creates a GenericEvent with the passed id.
     * 
     * @param id
     *            to associate with this event.
     */
    public GenericEvent(int id) {
        this.id = id;
    }

    /**
     * Returns the event id of this object. Generally all object have an id associated with them.
     * This id is used during dispatching to figure out which function to call in the EventListener.
     * 
     * @return the id associated with thsi object.
     */
    public int getID() {
        return id;
    }

}
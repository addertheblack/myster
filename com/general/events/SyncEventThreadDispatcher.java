package com.general.events;

import com.general.util.Util;

/**
 * Same as the SyncEventDispatcher but the events are dispatched on the event
 * thread instead of whatever thread caused the event to fire. The event that
 * caused the event to fire will block until the event has been completely
 * dispatched. Watchout for deadlocks if you use this class (see docs for
 * SyncEventDispatcher)
 */
public class SyncEventThreadDispatcher implements EventDispatcher {
    private SyncEventDispatcher dispatcher;

    /**
     *  
     */
    public SyncEventThreadDispatcher() {
        this.dispatcher = new SyncEventDispatcher();
    }

    public void addListener(EventListener listener) {
        dispatcher.addListener(listener);
    }

    public void removeListener(EventListener listener) {
        dispatcher.addListener(listener);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.general.events.EventDispatcher#fireEvent(com.general.events.GenericEvent)
     */
    public void fireEvent(final GenericEvent event) {
        if (Util.isEventDispatchThread()) {
            dispatcher.fireEvent(event);
        } else {
            try {
                Util.invokeAndWait(new Runnable() {
                    public void run() {
                        dispatcher.fireEvent(event);
                    }
                });
            } catch (InterruptedException ignore) {
                // I have no idea what to do here... We should throw an
                // interrupt
                // exception.. but we can't!
            }
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.general.events.EventDispatcher#getNumberOfListeners()
     */
    public int getNumberOfListeners() {
        return dispatcher.getNumberOfListeners();
    }
}
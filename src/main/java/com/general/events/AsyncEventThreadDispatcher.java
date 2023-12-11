package com.general.events;

import com.general.util.Util;

/**
 * Events are dispatched on the the event thread. The fireEvent method does not
 * block. Generally this is the event dispatcher you probably want to be using.
 */
public class AsyncEventThreadDispatcher implements EventDispatcher {
    private SyncEventDispatcher dispatcher;

    public AsyncEventThreadDispatcher() {
        this.dispatcher = new SyncEventDispatcher();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.general.events.EventDispatcher#addListener(com.general.events.EventListener)
     */
    public void addListener(EventListener listener) {
        dispatcher.addListener(listener);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.general.events.EventDispatcher#removeListener(com.general.events.EventListener)
     */
    public void removeListener(EventListener listener) {
        dispatcher.removeListener(listener);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.general.events.EventDispatcher#getNumberOfListeners()
     */
    public int getNumberOfListeners() {
        return dispatcher.getNumberOfListeners();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.general.events.EventDispatcher#fireEvent(com.general.events.GenericEvent)
     */
    public void fireEvent(final GenericEvent event) {
        Util.invokeLater(new Runnable() {
            public void run() {
                dispatcher.fireEvent(event);
            }
        });
    }
}
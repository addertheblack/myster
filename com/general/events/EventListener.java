package com.general.events;


/**
 * This class is used for general dispatching by a dispatcher.
 * 
 * @see com.general.events.EventDispatcher
 * @see com.general.events.GenericEvent
 */

public abstract class EventListener {

    /**
     * This is called by the dispatcher when an event has happened.
     * <p>
     * The events can be manually dispatched to different sub functions using a case table. Example:
     * 
     * <pre>
     * public final void fireEvent(GenericEvent event) {
     *     TransactionEvent e = (TransactionEvent) event;
     *     switch (e.getID()) {
     *     case TransactionEvent.TIMEOUT:
     *         transactionTimout(e);
     *         break;
     *     case TransactionEvent.REPLY:
     *         transactionReply(e);
     *         break;
     *     case TransactionEvent.CANCELLED:
     *         transactionCancelled(e);
     *         break;
     *     default:
     *         err();
     *     }
     * }
     * </pre>
     */
    public abstract void fireEvent(GenericEvent e);

    /**
     * This routine should be called if the event isn't in the case table. Its current
     * implementation is empty.
     */
    public void err() {
    }
}
package com.myster.transaction;

import com.general.events.EventListener;
import com.general.events.GenericEvent;

/**
 * Crappy low level listener for accessing information regarding the transaction
 * response.
 * <p>
 * This listener is not guaranteed to be called if the TransactionSocket was
 * cancelled.
 */
public abstract class TransactionListener extends EventListener {

    public final void fireEvent(GenericEvent event) {
        TransactionEvent e = (TransactionEvent) event;
        switch (e.getID()) {
        case TransactionEvent.TIMEOUT:
            transactionTimout(e);
            break;
        case TransactionEvent.REPLY:
            transactionReply(e);
            break;
        case TransactionEvent.CANCELLED:
            transactionCancelled(e);
            break;
        default:
            err();
        }
    }

    /**
     * Called when a replay packet has been received. Remember to check the
     * packet to make sure an error has not occurred (the packet may be an error
     * packet).
     * 
     * @param event
     *            received.
     */
    public abstract void transactionReply(TransactionEvent event);

    /**
     * Called when no response has been had to the transaction request.
     * 
     * @param event
     *            received.
     */
    public abstract void transactionTimout(TransactionEvent event);
    
    /**
     * Called when The request has been cancelled.
     * 
     * @param event
     *            received.
     */
    public void transactionCancelled(TransactionEvent event) {}
}
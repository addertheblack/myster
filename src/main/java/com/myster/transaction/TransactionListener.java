package com.myster.transaction;

/**
 * Crappy low level listener for accessing information regarding the transaction
 * response.
 * <p>
 * This listener is not guaranteed to be called if the TransactionSocket was
 * cancelled.
 */
public interface TransactionListener {
    /**
     * Called when a replay packet has been received. Remember to check the
     * packet to make sure an error has not occurred (the packet may be an error
     * packet).
     * 
     * @param event
     *            received.
     */
    void transactionReply(TransactionEvent event);

    /**
     * Called when no response has been had to the transaction request.
     * 
     * @param event
     *            received.
     */
    void transactionTimout(TransactionEvent event);
    
    /**
     * Called when The request has been cancelled.
     * 
     * @param event
     *            received.
     */
    default void transactionCancelled(TransactionEvent event) {}
}
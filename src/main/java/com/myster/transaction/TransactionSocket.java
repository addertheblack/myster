package com.myster.transaction;

import com.myster.net.DataPacket;

/**
 * This class is not thread safe however it doesn't need to be used on the event
 * thread either.
 */
public class TransactionSocket {
    private final int protocolNumber;

    private int uniqueIdFromTransactionManager;

    private boolean outstanding = false;

    public TransactionSocket(int protocolNumber) {
        this.protocolNumber = protocolNumber;
    }

    /**
     * Sends a datapacket of the protocol number sent in this constructor to
     * this object. Can only send ONE transaction.
     * 
     * @param dataPacket
     *            to send (protocol header information is added for you).
     * @param listener
     *            to notify upon events.
     */
    public void sendTransaction(DataPacket dataPacket, TransactionListener listener) {
        if (outstanding)
            throw new IllegalStateException("Cannot send two Transactions on this port!");
        uniqueIdFromTransactionManager = TransactionManager.sendTransaction(dataPacket,
                protocolNumber, listener);
        outstanding = true;
    }

    /**
     * Attempts to cancel this transaction. If cancelling was successful return
     * true. A transaction must have been sent before trying to cancel().
     * 
     * @return true if successfully cancelled. false otherwise.
     */
    public boolean cancel() {
        if (!outstanding)
            return false;
        
        return TransactionManager.cancelTransaction(uniqueIdFromTransactionManager);
    }
}
package com.myster.transaction;

import com.myster.net.DataPacket;

/**
 * This class is not thread safe however it doesn't need to be used by the event
 * thread either.
 */
public class TransactionSocket {
    int protocolNumber;

    int uniqueIdFromTransactionManager;

    boolean outstanding = false;

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

    //public Transaction sendTransactionBlocking(Transaction r) {
    //	return reply; //blocking version (not yet implemented)
    //}

    public void close() {
        //nothing (not nessesairy for transactions, they are one shot.)
    }

    /**
     * Attempts to cancel this transaction. If cancelling was successfull return
     * true. A transaction must have been sent before trying to cancel().
     * 
     * @return true if succesfully cancelled. false otherwise.
     */
    public boolean cancel() {
        if (!outstanding)
            return false;
        
        return TransactionManager.cancelTransaction(uniqueIdFromTransactionManager);
    }
}
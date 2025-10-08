package com.myster.transaction;

import com.myster.net.datagram.BadPacketException;

public interface TransactionProtocol {
   int getTransactionCode(); //returns the transaction code of

    // the transaction protocol.
    
    /**
     * @return and object associated with this transaction that would allow a client to attach
     *    and event listener to the goings of of this transaction
     */
    default Object getTransactionObject() {
        return null;
    }

    /**
     * This function is called by the TransactionManager implementation when a
     * datagram of the correct connection section number is received.
     * 
     * @param transaction
     *            received
     * @throws BadPacketException
     *             feel free to throw one if the packet is formated badly.
     */
    void transactionReceived(TransactionSender sender,
                             Transaction transaction,
                             Object transactionObject)
            throws BadPacketException;
}
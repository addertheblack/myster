package com.myster.transaction;

import com.myster.net.BadPacketException;

public abstract class TransactionProtocol {
    private TransactionSender sender;

    public abstract int getTransactionCode(); //returns the transaction code of
                                              // the transaction protocol.

    /**
     * This routine is so the transport manager can set the machanism through
     * wich you are supposed to send your outgoing packets.
     * 
     * It is a private API that I cannot close access to by those who inherit
     * from this class.
     * 
     * Presumably the setSender method will be replaced by a call to a static
     * manager inside this class (?)
     * 
     * Is there a book on building frameworks with data hiding for Java? :-)
     */
    protected final void setSender(TransactionSender sender) { //should only be
                                                               // acces by
                                                               // manager
        this.sender = sender; //weeeee...
    }

    protected final void sendTransaction(Transaction reply) {
        sender.sendTransaction(reply);
    }

    public abstract void transactionReceived(Transaction transaction)
            throws BadPacketException;
}
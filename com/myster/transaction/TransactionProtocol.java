package com.myster.transaction;

import com.myster.net.BadPacketException;

public abstract class TransactionProtocol {
    /*
     * Used to send packets back using the same port they were recieved on.
     */
    private TransactionSender sender;

    public abstract int getTransactionCode(); //returns the transaction code of

    // the transaction protocol.

    /**
     * This routine is so the transport manager can set the mechanism through
     * wich you are supposed to send your outgoing packets.
     * <p>
     * It is a private API that I cannot close access to by those who inherit
     * from this class.
     */
    final void setSender(TransactionSender sender) { //should only be
        // accessed by
        // manager
        this.sender = sender; //weeeee...
    }

    /**
     * Call this to send Transaction responses back using the same port they
     * were received on. If this TransactionProtocol has not been attached to a
     * TransactionManager then it will throw a NullPointerException.
     * <p>
     * Do not add this object to two TransactionManagers (on two different ports).
     * 
     * @param reply
     *            to send.
     */
    protected final void sendTransaction(Transaction reply) {
        sender.sendTransaction(reply);
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
    public abstract void transactionReceived(Transaction transaction) throws BadPacketException;
}
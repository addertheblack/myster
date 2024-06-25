package com.myster.transaction;

import com.myster.net.MysterAddress;

/**
 * Event passed to TransactionEventListeners. Contains context information for
 * the event.
 */
public class TransactionEvent {
    /*
     * Time it took to receive a reply.
     */
    private final long transactionTime;

    /*
     * Address + port the reply came from.
     */
    private final MysterAddress address;

    /*
     * the transaction response or null.
     */
    private final Transaction transaction;

    /**
     * Creates an event. Only the transaction manager transport implementation
     * should call this.
     * 
     * @param id
     *            either TIMEOUT of REPLY.
     * @param transactionTime
     *            time it took to receive a response or timeout.
     * @param address
     *            of remote server (+ port) or null if timeout
     * @param transaction
     *            received or null if timeout
     */
    TransactionEvent( long transactionTime, MysterAddress address, Transaction transaction) {
        this.transactionTime = transactionTime;
        this.address = address;
        this.transaction = transaction;
    }

    /**
     * Address the reply was received from or null if timeout.
     * 
     * @return address of the remote server + port.
     */
    public MysterAddress getAddress() {
        return address;
    }

    /**
     * The amount of time the Transaction sub system was waiting for a reply.
     * 
     * @return number of milliseconds it took to reply (or timeout) (or cancel).
     */
    public long getTransactionTime() { //ping time
        return transactionTime;
    }

    /**
     * Returns the transaction attached to this event. Returns null if
     * transaction is not available (because of timeout or cancelled)..
     */
    public Transaction getTransaction() {
        return transaction;
    }
}
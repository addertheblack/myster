package com.myster.transaction;

import com.general.events.GenericEvent;
import com.myster.net.MysterAddress;

/**
 * Event passed to TransactionEventListeners. Contains context information for
 * the event.
 */
public class TransactionEvent extends GenericEvent {
    /**
     * "ID" of the timeout event.
     */
    public static final int TIMEOUT = 1;

    /**
     * "ID" of the reply event.
     */
    public static final int REPLY = 2;
    
    /**
     * "IS of the cancel event.
     */
    public static final int CANCELLED = 3;

    /*
     * Time it took to receive a reply.
     */
    long transactionTime;

    /*
     * Address + port the reply came from.
     */
    MysterAddress address;

    /*
     * the transaction response or null.
     */
    Transaction transaction;

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
    TransactionEvent(int id, long transactionTime, MysterAddress address, Transaction transaction) {
        super(id);

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
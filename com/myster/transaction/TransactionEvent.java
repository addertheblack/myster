package com.myster.transaction;

import com.general.events.GenericEvent;
import com.myster.net.MysterAddress;

public class TransactionEvent extends GenericEvent {
    public static final int TIMEOUT = 1;

    public static final int REPLY = 2;

    long transactionTime;

    MysterAddress address;

    Transaction transaction;

    public TransactionEvent(int id, long transactionTime,
            MysterAddress address, Transaction transaction) {
        super(id);

        this.transactionTime = transactionTime;
        this.address = address;
        this.transaction = transaction;
    }

    public MysterAddress getAddress() {
        return address;
    }

    public long getTransactionTime() { //ping time
        return transactionTime;
    }

    /**
     * Returns the transaction attached to this event. Returns null if
     * transaction is not available..
     */
    public Transaction getTransaction() {
        return transaction;
    }
}
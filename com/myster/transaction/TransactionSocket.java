package com.myster.transaction;

import com.myster.net.DataPacket;

public class TransactionSocket {
    int protocolNumber;

    public TransactionSocket(int protocolNumber) {
        this.protocolNumber = protocolNumber;
    }

    public void sendTransaction(DataPacket t, TransactionListener l) {
        TransactionManager.sendTransaction(t, protocolNumber, l);
    }

    //public Transaction sendTransactionBlocking(Transaction r) {
    //	return reply; //blocking version (not yet implemented)
    //}

    public void close() {
        //nothing (not nessesairy for transactions, they are one shot.)
    }
}
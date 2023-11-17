package com.myster.net;

import java.io.IOException;

import com.myster.transaction.Transaction;

public interface StandardDatagramClientImpl<T> {
    public T getObjectFromTransaction(Transaction transaction)
            throws IOException;

    public Object getNullObject();

    public byte[] getDataForOutgoingPacket();

    public int getCode();
}
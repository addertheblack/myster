package com.myster.net;

import java.io.IOException;

import com.myster.transaction.Transaction;

public interface StandardDatagramClientImpl<T> {
    /**
     * @param transaction to convert into a reply object(s)
     * @return deserialized response
     * @throws IOException if format is wrong.
     */
    public T getObjectFromTransaction(Transaction transaction)
            throws IOException;

    /**
     * @deprecated no longer needed
     */
    @Deprecated
    public Object getNullObject();

    /**
     * @return data to send over the wire to the remote (serialzer)
     */
    public byte[] getDataForOutgoingPacket();

    /**
     * @return the int representing this transaction protocol
     */
    public int getCode();
}
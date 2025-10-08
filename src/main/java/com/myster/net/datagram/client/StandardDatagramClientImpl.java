package com.myster.net.datagram.client;

import java.io.IOException;

import com.myster.transaction.Transaction;

/**
 * This represents a packet exchange from the client point of view of a Transaction
 * 
 * @param <T> type of the data structure returned by the server in answer to your client request
 */
public interface StandardDatagramClientImpl<T> {
    /**
     * @param reply to convert into a reply object(s)
     * @return deserialized response
     * @throws IOException if format is wrong.
     */
    public T getObjectFromTransaction(Transaction reply)
            throws IOException;

    /**
     * @return data to send over the wire to the remote (serialzer)
     */
    public byte[] getDataForOutgoingPacket();

    /**
     * @return the int representing this transaction protocol
     */
    public int getCode();
}
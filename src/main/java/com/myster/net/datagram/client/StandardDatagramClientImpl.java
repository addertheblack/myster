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
     * @return serialized data to send to the remote peer
     * @throws IOException if the request cannot be serialized
     */
    public byte[] getDataForOutgoingPacket() throws IOException;

    /**
     * @return the int representing this transaction protocol
     */
    public int getCode();
}

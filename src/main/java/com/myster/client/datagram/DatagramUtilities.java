package com.myster.client.datagram;

import com.general.thread.AsyncContext;
import com.myster.client.stream.UnknownProtocolException;
import com.myster.transaction.Transaction;

public class DatagramUtilities {
    
    /**
     * This function must be called from the event thread.
     * @param transaction
     * @param listener
     */
    public static <R> boolean dealWithError(Transaction transaction,
                                        AsyncContext<R> context) {
        if (transaction.isError()) {
            //This NORMALLY means that the protocol was not understood.
            //Implementors *can* assume that this is the error without
            //checking.
            context.setException(new UnknownProtocolException(transaction.getErrorCode()));
            return true;
        }
        return false;
    }
}
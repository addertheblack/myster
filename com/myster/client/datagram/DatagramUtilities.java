package com.myster.client.datagram;

import com.general.thread.CallListener;
import com.myster.client.stream.UnknownProtocolException;
import com.myster.transaction.Transaction;

public class DatagramUtilities {
    
    /**
     * This function must be called from the event thread.
     * @param transaction
     * @param listener
     * @return
     */
    public static boolean dealWithError(Transaction transaction,
            CallListener listener) {
        if (transaction.isError()) {
            //This NORMALLY means that the protocol was not understood.
            //Implementors *can* assume that this is the error without
            //checking.
            listener.handleException(new UnknownProtocolException(transaction.getErrorCode()));
            return true;// there was an "error"
        }
        return false;
    }
}
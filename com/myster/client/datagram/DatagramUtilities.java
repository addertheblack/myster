package com.myster.client.datagram;

import com.myster.net.StandardDatagramEvent;
import com.myster.net.StandardDatagramListener;
import com.myster.transaction.Transaction;

public class DatagramUtilities {
    public static boolean dealWithError(Transaction transaction,
            StandardDatagramListener listener) {
        if (transaction.isError()) {
            //This NORMALLY means that the protocol was not understood.
            //Implementors *can* assume that this is the error without
            //checking.
            listener.error(new StandardDatagramEvent(transaction.getAddress(),
                    transaction.getTransactionCode(), new Integer(transaction
                            .getErrorCode())));
            return true;// there was an "error"
        }
        return false;
    }
}
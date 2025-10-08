package com.myster.transaction;

import com.myster.net.datagram.BadPacketException;

public class NotATransactionException extends BadPacketException {
    public NotATransactionException(String string) {
        super(string);
    }
}
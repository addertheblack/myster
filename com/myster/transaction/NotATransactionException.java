package com.myster.transaction;

import com.myster.net.BadPacketException;

public class NotATransactionException extends BadPacketException {
	public NotATransactionException(String string) {
		super(string);
	}
}
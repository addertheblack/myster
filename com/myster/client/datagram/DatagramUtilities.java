package com.myster.client.datagram;

import com.myster.transaction.Transaction;
import com.myster.net.StandardDatagramListener;
import com.myster.net.StandardDatagramEvent;

public class DatagramUtilities {
	public static boolean dealWithError(Transaction transaction, StandardDatagramListener listener) {
		if (transaction.isError()) {
			listener.error(new StandardDatagramEvent(transaction.getAddress(),
													transaction.getTransactionCode(),
													new Integer(transaction.getErrorCode())));
													
			return true;
		}
		
		return false;
	}
}
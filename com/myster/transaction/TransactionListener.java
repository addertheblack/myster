package com.myster.transaction;

import com.general.events.EventListener;
import com.general.events.GenericEvent;

public abstract class TransactionListener extends EventListener {

	public final void fireEvent(GenericEvent event) {
		TransactionEvent e=(TransactionEvent)event;
		switch (e.getID()) {
			case TransactionEvent.TIMEOUT:
				transactionTimout(e);
				break;
			case TransactionEvent.REPLY:
				transactionReply(e);
				break;
			default:
				err();
		}
	}
	
	public abstract void transactionReply(TransactionEvent e) ;
	public abstract void transactionTimout(TransactionEvent e) ;
}
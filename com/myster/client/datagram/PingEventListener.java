package com.myster.client.datagram;

import com.general.events.EventListener;
import com.general.events.GenericEvent;


public abstract class PingEventListener extends EventListener {
	public final void fireEvent(GenericEvent event) {
		PingEvent e=(PingEvent)event;
		switch (e.getID()) {
			case PingEvent.PING:
				pingReply(e);
				break;
			default:
				err();
		}
	}
	
	public abstract void pingReply(PingEvent e) ;
	
}

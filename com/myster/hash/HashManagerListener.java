package com.myster.hash;

import com.general.events.EventListener;
import com.general.events.GenericEvent;

public abstract class HashManagerListener extends EventListener {
	public void fireEvent(GenericEvent e) {
		HashManagerEvent event = (HashManagerEvent)e;
		
		switch (event.getID()) {
			case HashManagerEvent.ENABLED_STATE_CHANGED:
				enabledStateChanged(event);
				break;
			default:
				err();
		}
	}
	
	public abstract void enabledStateChanged(HashManagerEvent e) ;
}

package com.general.events;

import java.util.Vector;
import com.myster.util.MysterThread;

public class SyncEventDispatcher extends EventDispatcher {

	public void fireEvent(GenericEvent e) {
		Vector listeners=getListeners();
		synchronized (listeners) {
			for (int i=0; i<listeners.size(); i++) {
				try {
					((EventListener)(listeners.elementAt(i))).fireEvent(e);
				} catch (Exception ex) {
					ex.printStackTrace();
					//we don't want all events to not be delivered just because one handler died
				}
			}
		}
	}
}
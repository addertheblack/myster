package com.general.events;

import java.util.Vector;
import com.general.util.LinkedList;

public class SyncEventDispatcher extends EventDispatcher {
	private LinkedList queue=new LinkedList();
	private boolean isDispatching=false;
	
	public void fireEvent(GenericEvent e) {
		doCommandBacklog();
	
		synchronized (this) {
			isDispatching=true;
		}
		
		Vector listeners=getListeners();
		//synchronized (listeners) { //should not be nessesairy since no one should be adding while dispatching.
			for (int i=0; i<listeners.size(); i++) {
				try {
					((EventListener)(listeners.elementAt(i))).fireEvent(e);
				} catch (Exception ex) {
					ex.printStackTrace();
					//we don't want all events to not be delivered just because one handler died
				}
			}
		//}
		
		synchronized (this) {
			isDispatching=false;
		}
		doCommandBacklog();
	}
	
	private void doCommandBacklog() {
		while (queue.getSize()>0) {
			((ModifyListCommand)(queue.removeFromHead())).execute();
		}
	}
	
	public synchronized void addListener(EventListener l) {
		if (isDispatching) {
			queue.addToTail(new ModifyListCommand(l,true));
			return;
		}
		
		listeners.addElement(l);
	}
	
	public synchronized void removeListener(EventListener l) {
		if (isDispatching) {
			queue.addToTail(new ModifyListCommand(l,false));
			return;
		}
		
		listeners.removeElement(l);
	}
	
	private class ModifyListCommand {
		private final EventListener listener;
		private final boolean isAdd;
		
		public ModifyListCommand(EventListener listener, boolean isAdd) {
			this.listener=listener;
			this.isAdd=isAdd;
		}
		
		public void execute() {
			if (isAdd) {
				addListener(listener);
			} else {
				removeListener(listener);
			}
		}
	}
}
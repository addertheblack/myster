package com.general.events;

import java.util.Vector;
import com.myster.util.MysterThread;
import com.general.util.Channel;

public class SemiAsyncEventDispatcher extends EventDispatcher { //untested
	MysterThread thread;
	Channel channel=new Channel();

	public SemiAsyncEventDispatcher() {
		thread=new FireEvent(channel, getListeners());
		thread.start();
	}

	public void fireEvent(GenericEvent e) {
		channel.in.put(e);
	}
	
	public void finalize () {
		System.out.println("THIS HAS BEEN CALLED:");
		thread.end();
	}
	
	private static class FireEvent extends MysterThread {
		Channel channel;
		Vector listeners;
		
		public FireEvent(Channel c, Vector l) {
			channel=c;
			listeners=l;
		}
	
		public void run() {
			do {
				GenericEvent e=(GenericEvent)(channel.out.get());
				
				synchronized (listeners) {
					for (int i=0; i<listeners.size(); i++) {
						try {
							((EventListener)(listeners.elementAt(i))).fireEvent(e);
						} catch (Exception ex) {
							ex.printStackTrace();
						}
					}
				}
			} while (true);
		}
	}
}
package com.myster.search.ui;

import com.general.mclist.Sortable;
import com.myster.net.MysterAddress;
import com.myster.client.datagram.UDPPingClient;
import com.myster.client.datagram.PingEventListener;
import java.io.IOException;
import com.myster.client.datagram.PingEvent;


public class SortablePing implements Sortable {
	public static final int NOTPINGED=1000000;
	public static final int TIMEOUT=1000001;
	private  long number;
	
		
	public SortablePing(MysterAddress address) {
		number=NOTPINGED;
		
		try {
			
			UDPPingClient.ping(address,new MyPingEventListener() );
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
	
	public synchronized Object getValue() {
		return new Long(number);
	}
	
	public synchronized boolean isLessThan(Sortable temp) {
		if (temp == this)
	    	return false;
		if (!(temp instanceof SortablePing))
	    	return false;
	    Long n=(Long) temp.getValue();	
	    
	   	if (number<n.longValue()) return true;
	   	return false;
	}
	
	public synchronized boolean isGreaterThan(Sortable temp) {
		if (temp == this)
	    	return false;
		if (!(temp instanceof SortablePing))
	    	return false;
	    Long n=(Long) temp.getValue();
	    
	   	if (number>n.longValue()) return true;
	   	return false;
	}
	
	public synchronized boolean equals(Sortable temp) {
		if (temp == this)
	    	return true;
		if (!(temp instanceof SortablePing))
	    	return false;
	    Long n=(Long) temp.getValue();	
	    
	    if (number==n.longValue()) return true;
	    return false;
	}

	
	public synchronized void setNumber(long temp) {
		number=temp;
	}
	
	public String toString() {
		int temp=(int)number;
		
		if (temp==NOTPINGED) {
			return "-";
		} else if (temp==TIMEOUT) {
			return "Timeout";
		} else {
			return temp+"ms";
		}
	}
	
	private class MyPingEventListener extends PingEventListener {
		public void pingReply(PingEvent e) {
			if (e.isTimeout()) {
				setNumber(TIMEOUT);
			} else {
				setNumber(e.getPingTime());
			}
		}
	}

}
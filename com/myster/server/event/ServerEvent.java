/**
	Server event. Extend from this.
	
	Methods that consume these events should have ints.
*/

package com.myster.server.event;

import java.util.EventObject;
import com.general.events.GenericEvent;

public abstract class ServerEvent extends GenericEvent  {
	private String ip;
	private long time;
	private int section;
	
	
	
	public ServerEvent(int id, String ip, int section) {
		super(id); //ya!
		this.ip=ip;
		this.section=section;
		this.time=System.currentTimeMillis();
	}

	public String getIP() {
		return ip;
	}
	
	public int getSection() {
		return section;
	}
	
	public long getTimeStamp() {
		return time;
	}

}
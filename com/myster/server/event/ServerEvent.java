/**
	Server event. Extend from this.
	
	Methods that consume these events should have ints.
*/

package com.myster.server.event;

import com.general.events.GenericEvent;

import com.myster.net.MysterAddress;

public abstract class ServerEvent extends GenericEvent  {
	private MysterAddress address;
	private long time;
	private int section;
	
	
	
	public ServerEvent(int id, MysterAddress address, int section) {
		super(id); //ya!
		this.address	= address;
		this.section	= section;
		this.time		= System.currentTimeMillis();
	}

	public MysterAddress getAddress() {
		return address;
	}
	
	public int getSection() {
		return section;
	}
	
	public long getTimeStamp() {
		return time;
	}

}
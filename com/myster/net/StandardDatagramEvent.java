package com.myster.net;

import com.myster.net.MysterAddress;

public class StandardDatagramEvent {
	private final MysterAddress address;
	private final int sectionNumber;
	private final Object data;
	
	
	public StandardDatagramEvent(MysterAddress address, int sectionNumber, Object data) {
		this.address 		= address;
		this.sectionNumber	= sectionNumber;
		this.data			= data;
	}
	
	
	/**
	*	Returns null on timeout.
	*/
	public Object getData() {
		return data;
	}
	
	public MysterAddress getAddress() {
		return address;
	}
	
	public int getSectionNumber() {
		return sectionNumber;
	}
}

package com.myster.client.datagram;

import com.general.events.GenericEvent;
import com.general.net.ImmutableDatagramPacket;
import com.myster.net.MysterAddress;

public class PingEvent extends GenericEvent {
	public final static int PING=1;
	public int pingTime;
	private ImmutableDatagramPacket packet;
	private MysterAddress address;

	public PingEvent(int id, ImmutableDatagramPacket packet, int pingTime, MysterAddress address) {
		super(id);
		
		
		this.packet=packet;
		this.pingTime=pingTime;
		this.address=address;
	}
	
	public ImmutableDatagramPacket getPacket() {
		return packet;
	}
	
	public int getPingTime() {
		return (packet!=null?pingTime:-1);
	}
	
	public MysterAddress getAddress() {
		return address;
	}
	
	public boolean isTimeout() {
		return (getPingTime()==-1);
	}
}
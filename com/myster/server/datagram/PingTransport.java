package com.myster.server.datagram;

import com.myster.net.DatagramTransport;
import com.general.net.ImmutableDatagramPacket;
import com.myster.net.BadPacketException;
import com.myster.net.PingPacket;
import com.myster.net.PongPacket;
import com.myster.net.MysterAddress;

public class PingTransport extends DatagramTransport {
	static final int transportNumber=1346981447;
	
	public int getTransportCode() {
		return transportNumber;
	}
	
	public void packetReceived(ImmutableDatagramPacket immutablePacket) throws BadPacketException {
		PingPacket packet=new PingPacket(immutablePacket);
		
		sendPacket(PongPacket.getImmutablePacket(new MysterAddress(immutablePacket.getAddress(), immutablePacket.getPort())));
		System.out.println("Replied to a ping!");
	}
}

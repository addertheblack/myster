
package com.myster.net;

import com.general.net.ImmutableDatagramPacket;
import java.net.DatagramPacket;

public class PingPongPacket {
	public final static boolean PING=true;
	public final static boolean PONG=false;

	ImmutableDatagramPacket packet;
	final boolean packetType;

	public PingPongPacket(ImmutableDatagramPacket param_packet, boolean param_packetType) throws BadPacketException {
		packet=param_packet;
		packetType=param_packetType;
		
		if (packetType==PING?isAPingPacket(packet):isAPongPacket(packet)) {
		
		} else {
			if (packetType==PING) throw new NotAPingPacketException();
			else throw new NotAPongPacketException();
		}
	}
	
	public PingPongPacket(MysterAddress a, boolean param_packetType) {
		packet=getImmutablePacket(a, param_packetType);
		packetType=param_packetType;
	}
	
	public MysterAddress getAddress() {
		return new MysterAddress(packet.getAddress(), packet.getPort());
	}
	
	public int getPort() {
		return packet.getPort();
	}
	
	public static boolean isAPingPacket(ImmutableDatagramPacket packet) {
		return (new String(packet.getDataRange(0,4))).equals("PING");
	}
	
	public static boolean isAPongPacket(ImmutableDatagramPacket packet) {
		return (new String(packet.getDataRange(0,4))).equals("PONG");
	}
	
	public DatagramPacket toDatagramPacket() {
		return packet.getDatagramPacket();
	}
	
	public ImmutableDatagramPacket toImmutableDatagramPacket() {
		return packet;
	}
	
	public static ImmutableDatagramPacket getImmutablePacket(MysterAddress a, boolean param_packetType) {
		return new ImmutableDatagramPacket(a.getInetAddress(), a.getPort(),
				((param_packetType==PING?"PING":"PONG").getBytes()));
	}
}

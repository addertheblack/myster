package com.myster.net;

import com.general.net.ImmutableDatagramPacket;

public class PingPacket extends PingPongPacket {
	public PingPacket(ImmutableDatagramPacket param_packet) throws BadPacketException {
		super(param_packet, PingPongPacket.PING);
	}
	
	public PingPacket(MysterAddress param_address) {
		super(param_address, PingPongPacket.PING);
	}
	
	public static ImmutableDatagramPacket getImmutablePacket(MysterAddress a) {
		return PingPongPacket.getImmutablePacket(a, PingPongPacket.PING);
	}
}
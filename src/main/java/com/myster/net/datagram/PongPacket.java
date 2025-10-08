package com.myster.net.datagram;

import com.general.net.ImmutableDatagramPacket;
import com.myster.net.MysterAddress;

public class PongPacket extends PingPongPacket {
    public PongPacket(ImmutableDatagramPacket param_packet)
            throws BadPacketException {
        super(param_packet, PingPongPacket.PONG);
    }

    public PongPacket(MysterAddress param_address) throws BadPacketException {
        super(param_address, PingPongPacket.PONG);
    }

    public static ImmutableDatagramPacket getImmutablePacket(MysterAddress a) {
        return PingPongPacket.getImmutablePacket(a, PingPongPacket.PONG);
    }
}
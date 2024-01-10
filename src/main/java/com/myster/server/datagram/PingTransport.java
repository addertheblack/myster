package com.myster.server.datagram;

import com.general.net.ImmutableDatagramPacket;
import com.myster.net.BadPacketException;
import com.myster.net.DatagramSender;
import com.myster.net.DatagramTransport;
import com.myster.net.MysterAddress;
import com.myster.net.PingPacket;
import com.myster.net.PongPacket;

public class PingTransport extends DatagramTransport {
    private static final short TRANSPORT_NUMBER = 20553; // 'P', 'I' in network byte order

    public short getTransportCode() {
        return TRANSPORT_NUMBER;
    }

    @SuppressWarnings("unused")
    @Override
    public void packetReceived(DatagramSender sender, ImmutableDatagramPacket immutablePacket)
            throws BadPacketException {
        // We want the side effect of making sure this is a PING packet
        // The transport matcher only matches 'P' and 'I'
        new PingPacket(immutablePacket);

        sender.sendPacket(PongPacket.getImmutablePacket(new MysterAddress(
                immutablePacket.getAddress(), immutablePacket.getPort())));
    }

    @Override
    public boolean isEmpty() {
        return false;
    }
}
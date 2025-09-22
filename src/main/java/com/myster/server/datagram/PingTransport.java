package com.myster.server.datagram;

import com.general.net.ImmutableDatagramPacket;
import com.myster.application.MysterGlobals;
import com.myster.net.BadPacketException;
import com.myster.net.DatagramSender;
import com.myster.net.DatagramTransport;
import com.myster.net.MysterAddress;
import com.myster.net.PingPacket;
import com.myster.net.PongPacket;
import com.myster.server.ServerUtils;
import com.myster.tracker.Tracker;

public class PingTransport implements DatagramTransport {
    private static final short TRANSPORT_NUMBER = 20553; // 'P', 'I' in network byte order
    private Tracker manager;

    public PingTransport(Tracker manager) {
        if (manager == null ) {
            throw new NullPointerException("tracker can't be null");
        }
        this.manager = manager;
    }

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

        sender.sendPacket(PongPacket
                .getImmutablePacket(new MysterAddress(immutablePacket.getAddress(),
                                                      immutablePacket.getPort())));

        // this method call signals the tracker that's we've gotten a ping from
        // this ip address.
        // note it doens't mean there's a server on this port but if it's a LAN
        // address then the tracker might
        // do something
        manager.receivedPing(new MysterAddress(immutablePacket.getAddress(),
                                               MysterGlobals.DEFAULT_SERVER_PORT));
    }

    @Override
    public boolean isEmpty() {
        return false;
    }
}
package com.myster.net;

import com.general.net.ImmutableDatagramPacket;

/**
 * All classes that wish to be registered as official transport protocols Need to
 * use this "interface". Common usage is to register as a listener. At that time
 * the TransportManager will set the sender so Immutablepackets can be sent
 * through the correct manager using sendPacket().
 * 
 * This class cannot be registered with more that one TransportManager at once.
 * 
 * Transport Listener
 */

public interface DatagramTransport {
    /**
     * gets the transport code associated with this Datagramransport
     * 
     * @return the transport code.
     */
    public short getTransportCode();

    /**
     * Over-ride this method to receive packets with your transport code.
     * 
     * @param packet
     *            received
     * @throws BadPacketException
     *             feel free to throw this if the packet was badly formated.
     */
    public void packetReceived(DatagramSender sender, ImmutableDatagramPacket packet) throws BadPacketException;

    public boolean isEmpty();
}


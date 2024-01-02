package com.myster.net;

import com.general.net.ImmutableDatagramPacket;

/**
 * All classes who wish to be registered as official transport protocols Need to
 * use this "interface". Common usage is to register as a listener. At that time
 * the TransportManager will set the sender so Immutablepackets can be sent
 * through the correct manager using sendPacket().
 * 
 * This class cannot be registered with more that one TransportManager at once.
 * 
 * Transport Listener
 */

public abstract class DatagramTransport implements DatagramSender {
    private DatagramSender sender;

    /**
     * Use this function to send reply packets (or just to send packets) using
     * the correct port/address.
     */
    public void sendPacket(ImmutableDatagramPacket packet) {
        if (sender == null) {
            throw new IllegalStateException(
                    "Transport has not been added to the TransportManager yet!");
        }

        sender.sendPacket(packet);
    }

    /**
     * This routine is so the transport manager can set the mechanism through
     * which you are supposed to send your outgoing packets... Assuming you want
     * to send packets out on the same port as the one you received them.
     */
    final void setSender(DatagramSender sender) { //don't over-ride
        // (or even access)
        this.sender = sender; //weeeee...
    }

    /**
     * gets the transport code associated with this Datagramransport
     * 
     * @return the transport code.
     */
    public abstract short getTransportCode();

    /**
     * Over-ride this method to receive packets with your transport code.
     * 
     * @param packet
     *            received
     * @throws BadPacketException
     *             feel free to throw this if the packet was badly formated.
     */
    public abstract void packetReceived(ImmutableDatagramPacket packet) throws BadPacketException;

    public abstract boolean isEmpty();
}


package com.myster.net;

import com.general.net.ImmutableDatagramPacket;

/**
 * This class is used to send packets. Since the API is asynchronous, incoming
 * information is sent via an event. Outgoing information is sent via this
 * interface.
 */

public interface DatagramSender {
    /**
     * Send the packet onto it's destination. This is used by listeners to send reply packets.
     */
    public void sendPacket(ImmutableDatagramPacket p);
}
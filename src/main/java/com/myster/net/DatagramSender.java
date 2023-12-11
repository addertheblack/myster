package com.myster.net;

import com.general.net.ImmutableDatagramPacket;

/**
 * This class is used to send packets. Since the API is asynchronous, incmming
 * information is sent via an event. Outgoing information is sent via this
 * interface.
 */

public interface DatagramSender {
    public void sendPacket(ImmutableDatagramPacket p);
}
package com.myster.net.datagram.client;

import com.general.net.ImmutableDatagramPacket;
import com.myster.net.MysterAddress;

public class PingEvent {
    public int pingTime;

    private ImmutableDatagramPacket packet;

    private MysterAddress address;

    public PingEvent(ImmutableDatagramPacket packet, int pingTime, MysterAddress address) {

        this.packet = packet;
        this.pingTime = pingTime;
        this.address = address;
    }

    public ImmutableDatagramPacket getPacket() {
        return packet;
    }

    public int getPingTime() {
        return (packet != null ? pingTime : -1);
    }

    public MysterAddress getAddress() {
        return address;
    }

    public boolean isTimeout() {
        return (getPingTime() == -1);
    }
}
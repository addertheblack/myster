package com.general.net;

public interface AsyncDatagramListener {
    public void packetReceived(ImmutableDatagramPacket p);
}
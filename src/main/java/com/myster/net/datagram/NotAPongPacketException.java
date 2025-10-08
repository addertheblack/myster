package com.myster.net.datagram;

public class NotAPongPacketException extends BadPacketException {
    public NotAPongPacketException() {
        super("This Immutable packet is not a pong packet");
    }
}
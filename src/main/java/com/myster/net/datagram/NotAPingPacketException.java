package com.myster.net.datagram;

public class NotAPingPacketException extends BadPacketException {
    public NotAPingPacketException() {
        super("This Immutable packet is not a ping packet");
    }
}
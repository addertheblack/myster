package com.myster.net;

public class NotAPingPacketException extends BadPacketException {
    public NotAPingPacketException() {
        super("This Immutable packet is not a ping packet");
    }
}
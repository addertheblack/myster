package com.myster.net;

public class NotAPongPacketException extends BadPacketException {
    public NotAPongPacketException() {
        super("This Immutable packet is not a pong packet");
    }
}
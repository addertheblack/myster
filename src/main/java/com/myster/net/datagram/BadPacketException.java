package com.myster.net.datagram;

import java.io.IOException;

/** A malformed packet; subclasses may identify more specific packet-processing failures. */
public class BadPacketException extends IOException {
    public BadPacketException(String s) {
        super(s);
    }

    /** Preserves the original packet-processing failure for diagnostics. */
    public BadPacketException(String message, Throwable cause) {
        super(message, cause);
    }
}

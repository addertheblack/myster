package com.myster.net.stream.client;

import java.io.IOException;

//General class of protocol exceptions.
public class ProtocolException extends IOException {
    public ProtocolException() {
    }

    public ProtocolException(String s) {
        super(s);
    }
}


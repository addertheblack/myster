
package com.myster.client.stream;

public class UnknownProtocolException extends ProtocolException {
    final int err;

    public UnknownProtocolException(int e) {
        this(e, "");
    }

    public UnknownProtocolException(int e, String s) {
        super(s);
        err = e;
    }

    public int getError() {
        return err;
    }
}
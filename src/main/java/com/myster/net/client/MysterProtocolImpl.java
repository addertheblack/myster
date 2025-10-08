
package com.myster.net.client;

import com.myster.net.datagram.client.MysterDatagramImpl;
import com.myster.net.stream.client.MysterStreamImpl;

public class MysterProtocolImpl implements MysterProtocol {
    private final MysterDatagram datagram;
    private final MysterStream stream;

    public MysterProtocolImpl(MysterStreamImpl stream,
                              MysterDatagramImpl datagram) {
        this.stream = stream;
        this.datagram = datagram;
    }

    @Override
    public MysterDatagram getDatagram() {
        return datagram;
    }

    @Override
    public MysterStream getStream() {
        return stream;
    }
}


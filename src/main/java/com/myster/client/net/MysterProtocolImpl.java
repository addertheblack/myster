
package com.myster.client.net;

import com.myster.client.datagram.MysterDatagramImpl;
import com.myster.client.stream.MysterStreamImpl;

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

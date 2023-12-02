
package com.myster.client.net;

import com.myster.client.datagram.MysterDatagramImpl;
import com.myster.client.stream.MysterStreamImpl;

public class MysterProtocolImpl implements MysterProtocol {
    private final MysterDatagram datagramImpl;
    private final MysterStream streamImpl;

    public MysterProtocolImpl() {
        datagramImpl = new MysterDatagramImpl();
        streamImpl = new MysterStreamImpl();
    }
    
    @Override
    public MysterDatagram getDatagram() {
        return datagramImpl;
    }

    @Override
    public MysterStream getStream() {
        return streamImpl;
    }
}

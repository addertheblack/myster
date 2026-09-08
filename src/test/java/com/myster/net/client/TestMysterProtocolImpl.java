package com.myster.net.client;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

class TestMysterProtocolImpl {
    @Test
    void exposesTheSameProtocolCapabilitiesItWasGiven() {
        MysterStream stream = mock(MysterStream.class);
        MysterDatagram datagram = mock(MysterDatagram.class);
        DnsLookupProtocol dnsLookup = mock(DnsLookupProtocol.class);

        MysterProtocol protocol = new MysterProtocolImpl(stream, datagram, dnsLookup);

        assertSame(stream, protocol.getStream());
        assertSame(datagram, protocol.getDatagram());
        assertSame(dnsLookup, protocol.getDnsLookup());
    }
}

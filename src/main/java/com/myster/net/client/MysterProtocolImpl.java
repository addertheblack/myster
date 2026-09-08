
package com.myster.net.client;

import java.util.Objects;

/** Immutable aggregate of the client-side Myster protocol capabilities. */
public final class MysterProtocolImpl implements MysterProtocol {
    private final MysterDatagram datagram;
    private final MysterStream stream;
    private final DnsLookupProtocol dnsLookup;

    public MysterProtocolImpl(MysterStream stream,
                              MysterDatagram datagram,
                              DnsLookupProtocol dnsLookup) {
        this.stream = Objects.requireNonNull(stream, "stream");
        this.datagram = Objects.requireNonNull(datagram, "datagram");
        this.dnsLookup = Objects.requireNonNull(dnsLookup, "dnsLookup");
    }

    @Override
    public MysterDatagram getDatagram() {
        return datagram;
    }

    @Override
    public MysterStream getStream() {
        return stream;
    }

    @Override
    public DnsLookupProtocol getDnsLookup() {
        return dnsLookup;
    }
}

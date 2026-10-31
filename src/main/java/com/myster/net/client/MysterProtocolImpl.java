
package com.myster.net.client;

import java.util.Objects;

/** Immutable aggregate of the client-side Myster protocol capabilities. */
public final class MysterProtocolImpl implements MysterProtocol {
    private final MysterDatagram datagram;
    private final MysterStream stream;
    private final DnsLookupProtocol dnsLookup;
    private final java.security.PublicKey localPublicKey;

    public MysterProtocolImpl(MysterStream stream,
                              MysterDatagram datagram,
                              DnsLookupProtocol dnsLookup,
                              java.security.PublicKey localPublicKey) {
        this.stream = Objects.requireNonNull(stream, "stream");
        this.datagram = Objects.requireNonNull(datagram, "datagram");
        this.dnsLookup = Objects.requireNonNull(dnsLookup, "dnsLookup");
        this.localPublicKey = Objects.requireNonNull(localPublicKey, "localPublicKey");
    }

    /** Package protected for unit tests only */
    java.security.PublicKey getLocalPublicKey() {
        return localPublicKey;
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

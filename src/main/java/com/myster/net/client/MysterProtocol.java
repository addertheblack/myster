
package com.myster.net.client;

/** Client-side protocol stack exposed as transport and higher-level protocol capabilities. */
public interface MysterProtocol {
    MysterDatagram getDatagram();
    MysterStream getStream();
    DnsLookupProtocol getDnsLookup();
}

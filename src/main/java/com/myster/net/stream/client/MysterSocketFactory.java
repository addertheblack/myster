/*
 * 
 * Title: Myster Open Source Author: Andrew Trumper Description: Generic Myster
 * Code
 * 
 * This code is under GPL
 * 
 * Copyright Andrew Trumper 2000-2001
 */

package com.myster.net.stream.client;

import java.io.IOException;
import java.security.PublicKey;
import java.util.Optional;

import com.myster.identity.Identity;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.net.TLSSocket;

public class MysterSocketFactory {
    private static volatile Identity identity;

    public static void init(Identity identity) {
        MysterSocketFactory.identity = identity;
    }
    
    /**
     * @return a connected TLS socket, never null
     * @throws IOException if connection or TLS negotiation fails
     */
    public static MysterSocket makeStreamConnection(MysterAddress ip)
            throws IOException {
        return makeTLSConnection(ip, identity);
    }

    /**
     * Opens an authenticated stream connection and requires the server certificate to contain the
     * expected public key.
     *
     * @param ip remote TCP address
     * @param expectedServerPublicKey public key obtained through an authenticated discovery path
     * @return the pinned TLS socket, never null
     * @throws IOException if connection, TLS negotiation, or key verification fails
     */
    public static MysterSocket makeStreamConnection(MysterAddress ip,
            PublicKey expectedServerPublicKey) throws IOException {
        return TLSSocket.createClientSocket(ip, identity,
                Optional.of(java.util.Objects.requireNonNull(expectedServerPublicKey)));
    }

    public static void makeTransactionConnection(MysterAddress ip)
            throws IOException { //TBD to be done
        throw new IOException("");
    }
    
    /**
     * Creates a TLS connection using the provided identity for authentication.
     * The remote peer can extract the public key from the certificate during the TLS handshake.
     */
    private static TLSSocket makeTLSConnection(MysterAddress ip, Identity identity) 
            throws IOException {
        return TLSSocket.createClientSocket(ip, identity, Optional.empty());
    }
}

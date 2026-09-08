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
import java.net.Socket;
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
    
    /** Not used right now. We don't do unencrypted sockets */
    private static Socket makeTCPSocket(MysterAddress ip) throws IOException {
        Socket socket;

        socket = new Socket(ip.getInetAddress(), ip.getPort());

        socket.setSoTimeout(2 * 60 * 1000);// timeout 2 mins

        return socket;
    }

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
     * @return the pinned TLS socket
     * @throws IOException if connection, TLS negotiation, or key verification fails
     */
    public static MysterSocket makeStreamConnection(MysterAddress ip,
            PublicKey expectedServerPublicKey) throws IOException {
        TLSSocket socket = TLSSocket.createClientSocket(ip, identity,
                Optional.of(java.util.Objects.requireNonNull(expectedServerPublicKey)));
        if (socket == null) {
            throw new IOException("Server did not accept authenticated TLS");
        }
        return socket;
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

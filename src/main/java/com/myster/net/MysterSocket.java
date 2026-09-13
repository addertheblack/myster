
package com.myster.net;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.security.PublicKey;
import java.util.Optional;

import com.myster.net.stream.client.MysterDataInputStream;
import com.myster.net.stream.client.MysterDataOutputStream;

public abstract class MysterSocket implements AutoCloseable {
    public final MysterDataInputStream in;

    public final MysterDataOutputStream out;

    public MysterSocket(MysterDataInputStream i, MysterDataOutputStream o) {
        in = i;
        out = o;
    }

    /**
     * Returns the key whose possession the peer proved on this connection, if the transport
     * authenticates peers. This does not by itself associate the key with an expected server CID.
     * @throws IOException if an authenticated transport cannot retrieve its peer identity
     */
    public Optional<PublicKey> getAuthenticatedPeerKey() throws IOException {
        return Optional.empty();
    }

    public abstract InetAddress getInetAddress();

    public abstract InetAddress getLocalAddress();

    public abstract int getPort();

    public abstract int getLocalPort();

    public abstract MysterDataInputStream getInputStream() throws IOException;

    public abstract MysterDataOutputStream getOutputStream() throws IOException;

    public abstract void setSoLinger(boolean on, int val)
            throws SocketException;

    public abstract int getSoLinger() throws SocketException;

    public abstract void setSoTimeout(int timeout) throws SocketException;

    public abstract int getSoTimeout() throws SocketException;

    public abstract void close() throws IOException;

    public abstract String toString();
}
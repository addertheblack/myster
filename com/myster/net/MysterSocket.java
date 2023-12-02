
package com.myster.net;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;

public abstract class MysterSocket implements AutoCloseable {
    public final DataInputStream in;

    public final DataOutputStream out;

    public MysterSocket(DataInputStream i, DataOutputStream o) {
        in = i;
        out = o;
    }

    public abstract InetAddress getInetAddress();

    public abstract InetAddress getLocalAddress();

    public abstract int getPort();

    public abstract int getLocalPort();

    public abstract DataInputStream getInputStream() throws IOException;

    public abstract DataOutputStream getOutputStream() throws IOException;

    public abstract void setSoLinger(boolean on, int val)
            throws SocketException;

    public abstract int getSoLinger() throws SocketException;

    public abstract void setSoTimeout(int timeout) throws SocketException;

    public abstract int getSoTimeout() throws SocketException;

    public abstract void close() throws IOException;

    public abstract String toString();
}
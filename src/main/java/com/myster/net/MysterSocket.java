
package com.myster.net;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;

import com.myster.net.stream.client.MysterDataInputStream;
import com.myster.net.stream.client.MysterDataOutputStream;

public abstract class MysterSocket implements AutoCloseable {
    public final MysterDataInputStream in;

    public final MysterDataOutputStream out;

    public MysterSocket(MysterDataInputStream i, MysterDataOutputStream o) {
        in = i;
        out = o;
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
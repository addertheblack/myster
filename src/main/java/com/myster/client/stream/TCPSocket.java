package com.myster.client.stream;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;

import com.myster.bandwidth.ThrottledInputStream;
import com.myster.bandwidth.ThrottledOutputStream;
import com.myster.net.MysterSocket;

public class TCPSocket extends MysterSocket {
    Socket internalSocket;

    public TCPSocket(Socket socket) throws IOException {
        super(buildInput(socket), buildOutput(socket));
        internalSocket = socket;
    }

    private static DataInputStream buildInput(Socket socket) throws IOException {
        return new DataInputStream((new ThrottledInputStream(socket.getInputStream())));
    }

    private static DataOutputStream buildOutput(Socket socket) throws IOException {
        return new DataOutputStream(new ThrottledOutputStream(socket.getOutputStream()));
    }

    public InetAddress getInetAddress() {
        return internalSocket.getInetAddress();
    }

    public InetAddress getLocalAddress() {
        return internalSocket.getLocalAddress();
    }

    public int getPort() {
        return internalSocket.getPort();
    }

    public int getLocalPort() {
        return internalSocket.getLocalPort();
    }

    public DataInputStream getInputStream() throws IOException {
        return in;
    }

    public DataOutputStream getOutputStream() throws IOException {
        return out;
    }

    public void setTcpNoDelay(boolean on) throws SocketException {
        internalSocket.setTcpNoDelay(on);
    }

    public boolean getTcpNoDelay() throws SocketException {
        return internalSocket.getTcpNoDelay();
    }

    public void setSoLinger(boolean on, int val) throws SocketException {
        internalSocket.setSoLinger(on, val);
    }

    public int getSoLinger() throws SocketException {
        return internalSocket.getSoLinger();
    }

    public void setSoTimeout(int timeout) throws SocketException {
        internalSocket.setSoTimeout(timeout);
    }

    public int getSoTimeout() throws SocketException {
        return internalSocket.getSoTimeout();
    }

    public void close() throws IOException {
        internalSocket.close();
        in.close();
        out.close();
    }

    public String toString() {
        return internalSocket.toString();
    }
}
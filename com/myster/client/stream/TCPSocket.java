package com.myster.client.stream;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import com.myster.net.MysterSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;

public class TCPSocket extends MysterSocket {
	Socket internalSocket;
	
    public TCPSocket(Socket socket) throws IOException {
    	super(new DataInputStream(socket.getInputStream()), new DataOutputStream(socket.getOutputStream()));
    	internalSocket=socket;
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

    public InputStream getInputStream() throws IOException {
    	return internalSocket.getInputStream();
    }

    public OutputStream getOutputStream() throws IOException {
    	return internalSocket.getOutputStream();
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

    public synchronized void setSoTimeout(int timeout) throws SocketException {
    	internalSocket.setSoTimeout(timeout);
    }

    public synchronized int getSoTimeout() throws SocketException {
    	return internalSocket.getSoTimeout();
    }

    public synchronized void close() throws IOException {
    	internalSocket.close();
    }

    public String toString() {
    	return internalSocket.toString();
    }
}
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
import com.myster.bandwidth.*;

public class TCPSocket extends MysterSocket {
	Socket internalSocket;
	
    public TCPSocket(Socket socket) throws IOException {
    	super(new DataInputStream(new ThrottledInputStream(socket.getInputStream())), new DataOutputStream(new ThrottledOutputStream(socket.getOutputStream())));
    	internalSocket=socket;
    	
    	//myIn=internalSocket.getInputStream();
    	//myOut=internalSocket.getOutputStream();
    	
    	myIn=new ThrottledInputStream(internalSocket.getInputStream());
    	myOut=new ThrottledOutputStream(internalSocket.getOutputStream());
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

	public InputStream myIn;
    public InputStream getInputStream() throws IOException {
    	return myIn;
    }

	public OutputStream myOut;
    public OutputStream getOutputStream() throws IOException {
    	return myOut;
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

    public  int getSoTimeout() throws SocketException {
    	return internalSocket.getSoTimeout();
    }

    public void close() throws IOException {
    	internalSocket.close();
    }

    public String toString() {
    	return internalSocket.toString();
    }
}
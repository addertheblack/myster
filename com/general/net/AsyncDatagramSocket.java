package com.general.net;


/**
	This Socket can be used if you want to recieve 
	packets via an event and not have your main thread
	blocked on the socket.
*/

import java.net.*;
import java.io.*;
import com.general.util.LinkedList;

public class AsyncDatagramSocket {
	private AsyncDatagramSocketListener portListener;
	private DatagramSocket dsocket;
	private ManagerThread managerThread;
	private LinkedList queue=new LinkedList();

	public AsyncDatagramSocket(int port) throws IOException {
		dsocket=new DatagramSocket(port);
		dsocket.setSoTimeout(100);
		
		managerThread=new ManagerThread();
		managerThread.start();
	}
	
	public void setPortListener(AsyncDatagramSocketListener p) {
		portListener=p;
	}
	
	/**
		Asynchronous send. Will never block but consumes memory.
	*/
	public void sendPacket(ImmutableDatagramPacket p) {
		queue.addToTail(p);
	}
		
	public void close() {
		//try {
			dsocket.close();
		//} catch (IOException ex) {}
		
		managerThread.end();
	}
	
	private void doGetNewPackets() throws IOException {
		DatagramPacket p=new DatagramPacket(new byte[65536], 65536);
		
		
		try {
			dsocket.receive(p);
			if (portListener!=null) portListener.packetReceived(new ImmutableDatagramPacket(p));
		} catch (InterruptedIOException ex) {
		
		}
	}
	
	private void doSendNewPackets() throws IOException { 
		while (queue.getSize()>0) {
			ImmutableDatagramPacket p=(ImmutableDatagramPacket)(queue.removeFromHead());
			
			if (p!=null) {
				dsocket.send(p.getDatagramPacket());
			} //grrr..
		}
	}

	private class ManagerThread extends Thread {
		boolean endFlag=false;
		
		public void run() {
			for (;;) {
				if (endFlag) return;
				
				try {
					doGetNewPackets();
				} catch (IOException ex) {
					close();
				} catch (Exception ex) {
					ex.printStackTrace();
				}
				
				if (endFlag) return;
				
				try {
					doSendNewPackets();
				} catch (IOException ex) {
					close();
				} catch (Exception ex) {
					//todo.
				}
			}
		}
		
		public void end () {
			endFlag=true;
		}
	}
}

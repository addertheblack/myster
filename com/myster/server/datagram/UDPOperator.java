

package com.myster.server.datagram;

import java.net.*;
import java.io.*;
import java.util.Hashtable;
import com.myster.net.MysterAddress;
import com.general.net.AsyncDatagramSocketListener;
import com.general.net.ImmutableDatagramPacket;
import com.general.net.AsyncDatagramSocket;

public class UDPOperator implements AsyncDatagramSocketListener{
	int port;
	AsyncDatagramSocket dsocket=null;
	ImmutableDatagramPacket workingPacket;
	ImmutableDatagramPacket outgoingPacket;
	
	
	public UDPOperator (int p) {
		port=p;
	}
	
	public UDPOperator() {
		this(6669);
	}

	public void start() {
		try {
			dsocket=new AsyncDatagramSocket(port);
		} catch (IOException ex) {
			ex.printStackTrace();
		}
		dsocket.setPortListener(this);
	}

	public void packetReceived(ImmutableDatagramPacket workingPacket) {
		byte[] data=workingPacket.getData();
		byte[] comp=(new String("PING")).getBytes();
		if (data[0]==comp[0]&&data[1]==comp[1]&&data[2]==comp[2]&&data[3]==comp[3]) {
			byte[] outdata=(new String("PONG")).getBytes();
			outgoingPacket=new ImmutableDatagramPacket(workingPacket.getAddress(), workingPacket.getPort(), outdata);
			dsocket.sendPacket(outgoingPacket);
		}
		System.out.println("Replied to a ping!");
	}
}
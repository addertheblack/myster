
/* 

	Title:			Myster Open Source
	Author:			Andrew Trumper
	Description:	Generic Myster Code
	
	This code is under GPL

Copyright Andrew Trumper 2000-2001
*/

package com.myster.server;

import java.net.*;
import java.io.*;
import java.net.Socket;
import com.general.util.*;
import com.myster.server.stream.*;
import com.myster.filemanager.*;
import com.myster.server.event.*;
import java.util.Hashtable;
import com.myster.net.MysterAddress;
import com.myster.util.MysterThread;

/**
*	This class is responsible fore dealing with a conneciton with a client.
*	Basically it detects the type of service the client desires and imploys the appropriate protocal object.
*
*/

public class ConnectionManager extends MysterThread {
	final int BUFFERSIZE=512;
	Socket socket;
	ServerEventManager eventSender;
	BlockingQueue socketQueue;
	
	private DownloadQueue downloadQueue;
	
	private Hashtable connectionSections=new Hashtable();
	
	private ConnectionContext context;
	
	private static volatile int threadCounter=0;
	
	public ConnectionManager(BlockingQueue q, ServerEventManager eventSender, DownloadQueue downloadQueue, Hashtable connectionSections) {
		super("Server Thread "+(++threadCounter));
		
		socketQueue=q;
		this.downloadQueue=downloadQueue;
		this.eventSender=eventSender;
		this.connectionSections=connectionSections;
	}
	
	public void run() {
		while(true) {
			try {
				doConnection();
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}

	}
	
	private void doConnection() {
		try {
			socket=(Socket)(socketQueue.get());
		} catch (InterruptedException ex) {
			//should never happen
			return;//exit quickly in case it's being called by System.exit();
		}
		
		context=new ConnectionContext();
		context.socket=socket;
		context.downloadQueue=downloadQueue;
		context.serverAddress=new MysterAddress(socket.getInetAddress());
		int sectioncounter=0;
		try {
			DataInputStream i=new DataInputStream(socket.getInputStream());	//opens the connection

			int protocalcode;
			setPriority(Thread.MIN_PRIORITY);
			
			do {
				try {
					Thread.currentThread().yield();
					protocalcode=i.readInt();						//reads the type of conneciton requested
				} catch (Exception ex) {
					Thread.currentThread().yield();
					return;
				}
				
				sectioncounter++; //to detect if it was a ping.
				
				
				
				//Figures out which object to invoke for the connection type:
				//NOTE: THEY SAY RUN() NOT START()!!!!!!!!!!!!!!!!!!!!!!!!!
				String remoteip=socket.getInetAddress().getHostAddress();

				switch (protocalcode) {
					case 1:
						{DataOutputStream out=new DataOutputStream(socket.getOutputStream());
						out.write(1);}			//Tells the other end that the command is good bad!
						break;
					case 2:
						{DataOutputStream out=new DataOutputStream(socket.getOutputStream());
						out.write(1);}			//Tells the other end that the command is good bad!
						return;
					default:
						ConnectionSection section=(ConnectionSection)(connectionSections.get(new Integer(protocalcode)));
						if (section==null) {
							System.out.println("!!!System detects unknown protocol number : "+protocalcode);
							{DataOutputStream out=new DataOutputStream(socket.getOutputStream());
							out.write(0);}			//Tells the other end that the command is bad!
						} else {
							doSection(section, remoteip, context);
						} 
				}				
			} while(true);
		} catch (IOException ex) {
			
		} finally {
			
			if (sectioncounter==0) eventSender.fireOEvent(new OperatorEvent(OperatorEvent.PING, ""+socket.getInetAddress()));
			else eventSender.fireOEvent(new OperatorEvent(OperatorEvent.DISCONNECT, ""+socket.getInetAddress()));
			close(socket);
		}
		
	}
	
	private void fireConnectEvent(ConnectionSection d, String r, Object o) {
		eventSender.fireCEvent(new ConnectionManagerEvent(ConnectionManagerEvent.SECTIONCONNECT, r, d.getSectionNumber(), o));
	}
	
	private void fireDisconnectEvent(ConnectionSection d, String r, Object o) {
		eventSender.fireCEvent(new ConnectionManagerEvent(ConnectionManagerEvent.SECTIONDISCONNECT, r, d.getSectionNumber(), o));
	}
	
	private void doSection(ConnectionSection d, String remoteIP, ConnectionContext context) throws IOException {
		Object o=d.getSectionObject();
		context.sectionObject=o;
		fireConnectEvent(d, remoteIP, o);
		try {
			d.doSection(context);
		} finally {
			fireDisconnectEvent(d, remoteIP, o);
		}
	}
	
	private void waitMils(long w) {
		try {
			sleep(w);
		} catch (InterruptedException ex) {
		
		}
	}
	
	private static void close(Socket s) {
		try {s.close();} catch (Exception ex){}
	}
	
}
/* 

	Title:			Myster Open Source
	Author:			Andrew Trumper
	Description:	Generic Myster Code
	
	This code is under GPL

Copyright Andrew Trumper 2000-2001
*/
package com.myster.client.ui;

import java.awt.*;
import java.io.*;
import java.net.*;
import com.myster.util.MysterThread;
import com.myster.net.MysterSocket;
import com.myster.net.MysterAddress;
import com.myster.search.SearchResult;
import com.myster.util.Sayable;
import com.myster.net.MysterSocketFactory;
import com.general.util.TextSpinner;
import com.myster.type.MysterType;


public class FileListerThread extends MysterThread {
	ClientWindow w;
	Sayable msg;
	String ip;
	MysterType type;
	MysterSocket socket;

	public FileListerThread(ClientWindow a) {
		w=a;
		msg=w;
		this.ip=w.getCurrentIP();
		this.type=w.getCurrentType();
	}
	
	public void run() {
		setPriority(Thread.MAX_PRIORITY);
		DataOutputStream out;
		DataInputStream in;
		
		if (socket==null) {
			try {
				msg.say("Connecting to server...");
				socket=MysterSocketFactory.makeStreamConnection(new MysterAddress(ip));
			} catch (Exception ex) {
				msg.say("Server at that IP/Domain name is not responding.");
				return;
			}
		}
		
		main: {
			try {
				out=new DataOutputStream(socket.getOutputStream());
				in=new DataInputStream(socket.getInputStream());
			
				msg.say("Requesting File List...");
			
				out.writeInt(78);
				
				if (in.read()!=1) {
					msg.say("Server says it does not know how to send a file listing.");
					break main;
				}
				
				
				if (type==null) {
					msg.say("No type is selected.");
					break main;
				}
				
				msg.say("Requesting File List: "+type);
				
				out.write(type.getBytes());
				int numberoffiles=in.readInt();;

							
				msg.say("Receiving List of Size: "+numberoffiles);
				System.out.println("Receiving List of Size: "+numberoffiles);
	
				TextSpinner spinner=new TextSpinner();
				
				
				for (int i=0; i<numberoffiles; i++) {
				
					try {
						w.addItemToFileList(in.readUTF());
						msg.say("Requesting File List: "+type+ " " + spinner.getSpin());
					} catch (Exception ex) {
						msg.say("A Minor error occured, file list shorter than expected.");
						System.out.println("A Minor error occured, file list shorter than expected.");
						break;
					}
					
				}
				out.writeInt(2);
				
				in.read();
				
				msg.say("Requesting File List: "+type+ " " + spinner.getSpin()+ " Complete.");
				msg.say("Idle...");
			} catch (Exception ex) {
				msg.say("An unexpected error occured during the transfer of the file list.");
				ex.printStackTrace();
			}
		}
		
		try {
			socket.close();
		} catch (Exception ex) {
			msg.say("There was a problem closing the socket..");
			return;
		}
	
		
		
	}

}
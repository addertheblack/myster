/* 

	Title:			Myster Open Source
	Author:			Andrew Trumper
	Description:	Generic Myster Code
	
	This code is under GPL

Copyright Andrew Trumper 2000-2001
*/

package com.myster.client.ui;

import java.awt.event.*;
import java.awt.*;
import java.net.*;
import java.io.*;
import com.myster.util.*;
import com.myster.net.MysterSocket;
import com.myster.client.stream.StandardSuite;
import com.myster.net.MysterAddress;
import java.util.Vector;
import com.myster.net.MysterSocketFactory;


public class TypeListerThread extends MysterThread {
	ClientWindow container;
	Sayable msg;
	String ip;
	MysterSocket socket;	
		
	public TypeListerThread(ClientWindow w ) {
		container=w;
		msg=w;
		this.ip=w.getCurrentIP();
	}
	
	public void run() {
		try {
			msg.say("Requested Type List (UDP)...");
			
			com.myster.type.MysterType[] types = com.myster.client.datagram.StandardDatagramSuite.getTypes(
					new MysterAddress(ip));
			
			for (int i = 0; i<types.length; i++) {
				container.addItemToTypeList(""+types[i]);
			}
			
			msg.say("Idle...");
		} catch (IOException exp) {
			try {
				msg.say("Connecting to server...");
				socket=MysterSocketFactory.makeStreamConnection(new MysterAddress(ip));
			} catch (IOException ex) {
				msg.say("Could not connect, server is unreachable...");
				return;
			}
			
			try {
				msg.say("Requesting File Type List...");
				
				Vector typeList=StandardSuite.getTypes(socket);
				
				msg.say("Adding Items...");
				for (int i=0; i<typeList.size(); i++){
					container.addItemToTypeList(typeList.elementAt(i).toString());
				}
				
				msg.say("Idle...");
			} catch (IOException ex) {
				msg.say("Could not get File Type List from specified server.");
			} finally {
				try {
					socket.close();
				} catch (Exception ex) {}
			}
		}
	}
}
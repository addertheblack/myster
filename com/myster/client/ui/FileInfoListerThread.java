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
import com.myster.mml.*;
import com.myster.util.MysterThread;
import java.util.Vector;
import com.myster.net.MysterSocket;
import com.myster.client.stream.StandardSuite;
import com.myster.net.MysterAddress;
import com.myster.search.*;
import com.general.util.KeyValue;
import com.general.util.MessageField;
import com.myster.net.MysterSocketFactory;

public class FileInfoListerThread extends MysterThread {
	ClientWindow w;

	public FileInfoListerThread(ClientWindow w) {
		this.w=w;
	}
	
	public void run() {
		MysterSocket socket=null;
		DataOutputStream out;
		DataInputStream in;
		MessageField msg=w.getMessageField();
		
		try {
			msg.say("Connecting to server...");
			socket=MysterSocketFactory.makeStreamConnection(new MysterAddress(w.getCurrentIP()));
			msg.say("Getting file information...");
			
			RobustMML mml=new RobustMML(StandardSuite.getFileStats(socket, new MysterFileStub(new MysterAddress(w.getCurrentIP()), w.getCurrentType(), w.getCurrentFile())));
			
			msg.say("Parsing file information...");
			
			KeyValue keyvalue=new KeyValue();
			Vector mmllisting=mml.list("/");
			keyvalue.addValue("File Name",w.getCurrentFile());
			keyvalue.addValue("Of Type",w.getCurrentFile()); 

			for (int i=0; i<mmllisting.size(); i++) {
				keyvalue.addValue((String)(mmllisting.elementAt(i)), mml.get("/"+(String)(mmllisting.elementAt(i))));
			}
			
			w.showFileStats(keyvalue);
			
			msg.say("Idle...");
			
		} catch (IOException ex) {
			msg.say("Transmission errorm could not get File Stats.");
		} finally {
			try {
				socket.close();
			} catch (Exception ex) {}
		}
	}
}
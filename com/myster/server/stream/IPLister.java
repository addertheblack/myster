package com.myster.server.stream;

import com.myster.tracker.*;
import java.net.*;
import java.io.*;
import com.myster.server.ConnectionContext;
import com.myster.net.MysterAddress;

/* 

	Title:			Myster Open Source
	Author:			Andrew Trumper
	Description:	Generic Myster Code
	
	This code is under GPL

Copyright Andrew Trumper 2000-2001
*/

import com.general.util.*;

public class IPLister extends ServerThread {
	public static final int NUMBER=10;
	
	public int getSectionNumber() {
		return NUMBER;
	}
	
	/**
	 * Protocal: Send 10 (done) Send TYPE(4 bytes) get String array (get a bunch of strings) NO length sent
	 */
	
	
	public void section(ConnectionContext context) throws IOException {
		DataInputStream in=new DataInputStream(context.socket.getInputStream());
		DataOutputStream out=new DataOutputStream(context.socket.getOutputStream());

		MysterServer[] topten;

		byte[] type=new byte[4];
		in.readFully(type);
		
		IPListManagerSingleton.getIPListManager().addIP(new MysterAddress(context.socket.getInetAddress()));
		
		topten=IPListManagerSingleton.getIPListManager().getTop(new String(type),100);
		if (topten!=null) {
			for (int i=0; i<topten.length; i++) {
				if (topten[i]==null) break;
				out.writeUTF(topten[i].getAddress().getIP());
			}
		}
		out.writeUTF("");	//"" Signals the end of the list!
	}

}

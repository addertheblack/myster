/* 

	Title:			Myster Open Source
	Author:			Andrew Trumper
	Description:	Generic Myster Code
	
	This code is under GPL

Copyright Andrew Trumper 2000-2001
*/
package com.myster.server.stream;

import java.net.*;
import java.io.*;
import java.util.Vector;
import java.util.StringTokenizer;
import com.general.util.*;
import com.myster.server.event.*;
import com.general.events.EventDispatcher;
import com.myster.server.ConnectionContext;
import com.myster.filemanager.FileTypeListManager;
import com.myster.type.MysterType;

public class RequestSearchThread extends ServerThread {
	
	public static final int NUMBER=35;
	
	public int getSectionNumber() {
		return NUMBER;
	}
	
	public Object getSectionObject() {
		return new ServerSearchDispatcher();
	}
	
	/**
	*	Protocal: Send 35 Send Type (4 bytes) get Set of strings of names of files that match.
	*/
	
	public void section(ConnectionContext c) throws IOException {
		DataInputStream in=new DataInputStream(c.socket.getInputStream());
		DataOutputStream out=new DataOutputStream(c.socket.getOutputStream());
		
		ServerSearchDispatcher dispatcher=(ServerSearchDispatcher)(c.sectionObject);
		
		byte[] type=new byte[4];
		String searchstring;
		String tempstring;
		
		in.readFully(type);
		searchstring=new String(in.readUTF());
		
		String[] stringarray;
		
       	stringarray = FileTypeListManager.getInstance().getDirList(new MysterType(type), searchstring);
		
		dispatcher.fireEvent(new ServerSearchEvent(ServerSearchEvent.REQUESTED, c.socket.getInetAddress().getHostAddress(), NUMBER, searchstring, new String(type), null));

		if (stringarray!=null) {
			for (int j=0; j<stringarray.length; j++) {
				out.writeUTF(stringarray[j]);

				dispatcher.fireEvent(new ServerSearchEvent(ServerSearchEvent.RESULT, c.socket.getInetAddress().getHostAddress(), NUMBER, searchstring,new String(type),  stringarray[j]));

			}
		}
		
		out.writeUTF("");
	}
}

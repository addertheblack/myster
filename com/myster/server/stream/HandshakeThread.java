package com.myster.server.stream;
/* 

	Title:			Myster Open Source
	Author:			Andrew Trumper
	Description:	Generic Myster Code
	
	This code is under GPL

Copyright Andrew Trumper 2000-2001
*/

import java.net.*;
import java.io.*;
import com.general.util.*;
import com.myster.mml.MML;
import com.myster.filemanager.*;
import com.myster.pref.Preferences;
import Myster;
import com.myster.tracker.*;
import com.myster.server.ServerFacade;
import com.myster.server.ConnectionContext;
import com.myster.type.MysterType;

public class HandshakeThread extends ServerThread {
	public static final int NUMBER=101;
	
	public int getSectionNumber() {
		return NUMBER;
	}
	
	public void section(ConnectionContext context) throws IOException {
		byte[] b=new byte[4];
		
		String temp=""+getMMLToSend(b);
		
		context.socket.out.writeUTF(temp);
	}

	private MML getMMLToSend(byte[] b) {
		try {
			MML mml=new MML();
			Preferences prefs;
			prefs=Preferences.getInstance();
			
			String tempstring=prefs.query(Myster.SPEEDPATH);
			if (!(tempstring.equals(""))) {
				mml.put("/Speed", tempstring);
			}
			
			//mml.addMML(new MML(""+ftm.getNumberOfFiles(b),"NumberOfFiles"));
			
			mml.put("/Myster Version", "1.0");
			
			tempstring=prefs.query(Myster.ADDRESSPATH);
			if (!(tempstring.equals(""))) {						//If there is no value for the address it doesn't send this info.
				mml.put("/Address", tempstring);	//Note: "" is no data. see qweryValue();
			}
			
			getNumberOfFilesMML(mml);	//Adds the number of files data.
			
			String ident=ServerFacade.getIdentity();
			if (ident!=null) {
				if (!ident.equals("")) {
					mml.put("/ServerIdentity", ident);	
				}
			}
			
			mml.put("/uptime", ""+(System.currentTimeMillis() - Myster.getLaunchedTime()));
			
			return mml;
		} catch (Exception ex) {
			System.out.println("Error in getMMLtoSend()");
			ex.printStackTrace();
			return null;
		}
		 
	}
	
	private MML getNumberOfFilesMML(MML mml) { //in-line
		FileTypeListManager filemanager=FileTypeListManager.getInstance();
		
		MysterType[] filetypelist=filemanager.getFileTypeListing();
		String dir="/numberOfFiles/";
		
		
		for (int i=0; i<filetypelist.length; i++){
			mml.put(dir+filetypelist[i], ""+filemanager.getNumberOfFiles(filetypelist[i]));
		}
		
		return mml;
	}


}
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

import com.myster.search.MysterFileStub;
import com.myster.net.MysterAddress;

public class FileListAction implements ActionListener {
	ClientWindow w;
	
	
	public FileListAction(ClientWindow w) {
		this.w=w;
	} 
	
	static volatile long timeOfLast=0;
	
	public synchronized void actionPerformed(ActionEvent a) {
		try {
			if (System.currentTimeMillis()-timeOfLast<1000) return;
			timeOfLast=System.currentTimeMillis();
			MysterFileStub stub = new MysterFileStub(new MysterAddress(w.getCurrentIP()), w.getCurrentType(), a.getActionCommand());
			com.myster.client.stream.StandardSuite.downloadFile(stub.getMysterAddress(), stub);
		} catch (java.io.IOException ex) {
			com.general.util.AnswerDialog.simpleAlert(w, "Could not connect to server.");
		}
	}

}
/* 

	Title:			Myster Open Source
	Author:			Andrew Trumper
	Description:	Generic Myster Code
	
	This code is under GPL

Copyright Andrew Trumper 2000-2001
*/
package com.myster.client.ui;

import com.myster.client.stream.DownloaderThread;
import com.myster.search.MysterFileStub;
import com.myster.net.MysterAddress;

import java.awt.event.*;
import java.awt.*;
import java.net.UnknownHostException;

public class FileListAction implements ActionListener {
	ClientWindow w;
	
	
	public FileListAction(ClientWindow w) {
		this.w=w;
	} 
	
	public void actionPerformed(ActionEvent a) {
		try {
			(new DownloaderThread(new MysterFileStub(new MysterAddress(w.getCurrentIP()), w.getCurrentType(), a.getActionCommand()))).start();
		} catch (UnknownHostException ex) {
			//... nothing
		}
	}

}
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
import com.myster.util.MysterThread;

public class FileStatsAction implements ItemListener {
	ClientWindow w;
	MysterThread t;
	
	public FileStatsAction(ClientWindow w) {
		this.w=w;
	} 
	
	public void itemStateChanged(ItemEvent a) {
		synchronized (a){
			try {t.end();} catch (Exception ex) {}
			if (a.getStateChange()==ItemEvent.SELECTED) {
				w.clearFileStats();
				t=(new FileInfoListerThread(w));
				t.start();
			} else {
				w.clearFileStats();
			}
		}
	}
}
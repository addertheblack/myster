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

public class FileTypeSelectListener implements ItemListener {
	ClientWindow w;
	MysterThread t;
	
	public FileTypeSelectListener(ClientWindow w) {
		this.w=w;
	} 
	
	public void itemStateChanged(ItemEvent e) {
		synchronized (this){
			try {t.end();} catch (Exception ex) {}
			if (e.getStateChange()==ItemEvent.SELECTED) {
				w.clearFileList();
				t=(new FileListerThread(w));//a, w, w.getCurrentIP(), w.getCurrentType()));
				t.start();
			} else {
				w.clearFileList();
			}
		}
	}
}
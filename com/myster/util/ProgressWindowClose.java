/* 

	Title:			Myster Open Source
	Author:			Andrew Trumper
	Description:	Generic Myster Code
	
	This code is under GPL

Copyright Andrew Trumper 2000-2001
*/

package com.myster.util;

import java.awt.*;
import java.awt.event.*;
import com.general.util.SafeThread;

public class ProgressWindowClose extends WindowAdapter {
	SafeThread t;

	public ProgressWindowClose(SafeThread t) {
		this.t=t;
	}
	
	public void windowClosing(WindowEvent e) {
		try {
			//t.suspend();
			t.end();
		} catch (Exception ex) {}
		e.getWindow().setVisible(false);
		e.getWindow().dispose();
	}

}
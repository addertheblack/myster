/* 

	Title:			Myster Open Source
	Author:			Andrew Trumper
	Description:	Generic Myster Code
	
	This code is under GPL

Copyright Andrew Trumper 2000-2001
*/


package com.myster.menubar.event;

import com.myster.tracker.ui.*;
import java.awt.*;
import java.awt.event.*;

public class TrackerWindowAction implements ActionListener {

	
	public void actionPerformed(ActionEvent e) {
		TrackerWindow.getInstance().setVisible(true);
		TrackerWindow.getInstance().toFront();
		TrackerWindow.getInstance().setEnabled(true);
	}

}
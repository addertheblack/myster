/* 

	Title:			Myster Open Source
	Author:			Andrew Trumper
	Description:	Generic Myster Code
	
	This code is under GPL

Copyright Andrew Trumper 2000-2001
*/

package com.myster.menubar.event;

import java.awt.*;
import java.awt.event.*;
import com.myster.server.ui.*;

public class StatsWindowAction implements ActionListener {
	
	public void actionPerformed(ActionEvent e) {
		ServerStatsWindow.getInstance().setVisible(true);
	}

}
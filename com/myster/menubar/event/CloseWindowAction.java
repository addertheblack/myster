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

public class CloseWindowAction implements ActionListener {
	Frame w;
	
	public CloseWindowAction(Frame w) {
		this.w=w;
	}
	
	public void actionPerformed(ActionEvent e) {
		w.show(false);
	
	}

}
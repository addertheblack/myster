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
import com.myster.tracker.ui.AddIPDialog;

public class AddIPMenuAction implements ActionListener {
	Frame f;
	
	public AddIPMenuAction(Frame f) {
		this.f=f;
	}
	
	public void actionPerformed(ActionEvent e) {
		AddIPDialog a=new AddIPDialog();
		a.show();
	}

}
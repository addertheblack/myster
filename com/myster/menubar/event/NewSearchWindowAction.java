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
import com.myster.search.ui.SearchWindow;

public class NewSearchWindowAction implements ActionListener {
	
	public void actionPerformed(ActionEvent e) {
		(new SearchWindow()).setVisible(true);
	}

}
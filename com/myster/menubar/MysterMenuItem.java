/* 

	Title:			Myster Open Source
	Author:			Andrew Trumper
	Description:	Generic Myster Code
	
	This code is under GPL

Copyright Andrew Trumper 2000-2001
*/

package com.myster.menubar;

import java.awt.*;
import java.awt.event.*;

public class MysterMenuItem extends MenuItem {
	/**
	*	basically a MenuItem with a customized constructor 
	*
	*/

	MysterMenuItem(String s, ActionListener a) {
		this(s,a,-1);
	}
	
	MysterMenuItem(String s, ActionListener a, int shortcut ) {
		this(s,a,shortcut,false);
	}
	
	MysterMenuItem(String s, ActionListener a, int shortcut, boolean useShift ) {
		super(s);
		if (a!=null) {
			addActionListener(a);
		}
		
		if (shortcut!=-1) {
			setShortcut(new MenuShortcut(shortcut,useShift));
		}
	}
}
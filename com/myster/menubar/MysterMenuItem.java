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

public class MysterMenuItem extends MenuItem{
	/**
	*	basically a MenuItem with a customized constructor 
	*
	*/

	MysterMenuItem(String s, ActionListener a) {
		super(s);
		if (a!=null) {
			addActionListener(a);
		}
	}
}
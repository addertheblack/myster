/* 

	Title:			Myster Open Source
	Author:			Andrew Trumper
	Description:	Generic Myster Code
	
	This code is under GPL

Copyright Andrew Trumper 2000-2001
*/
package com.myster.client.ui;

import java.awt.*;
import java.io.*;
import java.lang.*;
import java.net.*;

public class FileList extends List {


	public FileList() {
		super(5,false);
		setSize(200,400);
	}	
	public Dimension getMinimumSize() {
		return new Dimension(0,0);
	}
	
	public Dimension getPreferredSize() {
		return getSize();
	}
	
}
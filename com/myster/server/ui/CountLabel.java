/* 
	Main.java

	Title:			Server Stats Window Test App
	Author:			Andrew Trumper
	Description:	An app to test the server stats window
*/

package com.myster.server.ui;


import java.awt.*;
import com.general.tab.*;
import java.awt.image.*;
import com.general.mclist.*;


public class CountLabel extends Label {
	int value=0;
	
	public CountLabel(String s) {
		super(s);
	}
	
	public int getValue() {
		return value;
	}
	
	public synchronized void setValue(int i) {
		value=i;
		setUpdateLabel();
	}
	
	public synchronized void increment() {
		value++;
		setUpdateLabel();
	}
	
	public synchronized void decrement() {
		value--;
		setUpdateLabel();
	}
	
	private synchronized void setUpdateLabel() {
		setText(""+value);
	}
	
	

}
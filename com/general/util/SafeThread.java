/* 

	Title:			Myster Open Source
	Author:			Andrew Trumper
	Description:	Generic Myster Code
	
	This code is under GPL

Copyright Andrew Trumper 2000-2001
*/

package com.general.util;

public abstract class SafeThread extends Thread {
	protected boolean endFlag=false;
	
	public SafeThread() {}
	
	public SafeThread(String name) {
		super(name);
	}
	
	public void flagToEnd() {
		endFlag=true;
	}
	
	public void end() {
		flagToEnd();
		try {
			join();
		} catch (InterruptedException ex) {}//I hope this doens't come back and bite me.
	}
	
	public boolean isFlaggedToEnd() {
		return endFlag;
	}
}
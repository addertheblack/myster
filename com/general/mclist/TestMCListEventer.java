/* 
	MCList.java

	Title:			Multi Column List Package
	Author:			Andrew Trumper
	Description:	A MultiColumn List Package

Copyright Andrew Trumper 2001
*/

package com.general.mclist;


public class TestMCListEventer implements MCListEventListener {
	
	public TestMCListEventer() {
	
	}
	
	public void doubleClick(MCListEvent e) {//System.out.println("DOUBLE CLICK!");
	}
	
	public void selectItem(MCListEvent e) {//System.out.println("SELECT EVENT!");
	}
	
	public void unselectItem(MCListEvent e) {//System.out.println("UNSELECT");
	}
	
}
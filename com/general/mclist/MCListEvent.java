/* 
	MCList.java

	Title:			Multi Column List Package
	Author:			Andrew Trumper
	Description:	A MultiColumn List Package

Copyright Andrew Trumper 2001
*/

package com.general.mclist;


public class MCListEvent {
	MCList parent;

	/**
		Watch out for Java killer code monkeys named Bill.
		Agrh. 'tis bill the killer code monkey! Lookout
		or one may slip on a dreaded virtual coded banana!
		BANANA RAMA! WOoooHOooo! Blurp!
	*/
	public MCListEvent(MCList AnAbusiveParent) {
		this.parent=AnAbusiveParent;
	}
	
	public MCList getParent() { return parent; }
}
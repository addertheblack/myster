/* 
	MCList.java

	Title:			Multi Column List Package
	Author:			Andrew Trumper
	Description:	A MultiColumn List Package

Copyright Andrew Trumper 2001
*/

package com.general.mclist;

public class GenericMCListItem extends MCListItemInterface {
	protected Sortable[] sortables;
	protected Object object;
	
	public GenericMCListItem(Sortable[] s) {
		this(s,null);
	}
	
	public GenericMCListItem(Sortable[] s, Object o) {
		sortables=s;
		object=o;
	}
	
	public Sortable getValueOfColumn(int i) {
		//if (i>=sortables.length||i<0) return "ERR"; 
		return sortables[i];
	}

	public Object getObject() {
		return object;
	}
}
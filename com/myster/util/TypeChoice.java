/* 

	Title:			Myster Open Source
	Author:			Andrew Trumper
	Description:	Generic Myster Code
	
	This code is under GPL

Copyright Andrew Trumper 2000-2001
*/

package com.myster.util;

import java.awt.Choice;

public class TypeChoice extends Choice {

	public TypeChoice() {
		addItemsToChoice();
	}

	public String getType() {
		return getSelectedItem().substring(0,4);	
	}
	
	public String getType(int i) {
		return getItem(i).substring(0,4);
	}

	private void addItemsToChoice() {
		TypeDescription list[]=TypeDescription.loadTypeAndDescriptionList(this);
		for (int i=0; i<list.length; i++) {
			add(list[i].getType()+" ("+list[i].getDescription()+")");
		}
	}
}
/* 

	Title:			Myster Open Source
	Author:			Andrew Trumper
	Description:	Generic Myster Code
	
	This code is under GPL

Copyright Andrew Trumper 2000-2001
*/

package com.myster.util;

import java.awt.Choice;
import com.myster.type.TypeDescription;
import com.myster.type.TypeDescriptionList;
import com.myster.type.MysterType;

public class TypeChoice extends Choice {
	TypeDescription[] types;

	public TypeChoice() {
		addItemsToChoice();
	}

	public MysterType getType() {
		return getType(getSelectedIndex());	
	}
	
	public MysterType getType(int i) {
		return types[i].getType();
	}

	private void addItemsToChoice() {
		types = TypeDescriptionList.getDefault().getEnabledTypes();
		for (int i = 0; i < types.length; i++) {
			add(types[i].getDescription()+" ("+types[i].getType()+")");
		}
	}
}
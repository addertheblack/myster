/* 

	Title:			Myster Open Source
	Author:			Andrew Trumper
	Description:	Generic Myster Code
	
	This code is under GPL

Copyright Andrew Trumper 2000-2001
*/

package com.myster.type;

public class TypeDescription {
	MysterType 	type;
	String 		description;
	String[]	extensions;
	boolean		isArchived;
	boolean		isEnabledByDefault;

	public TypeDescription(MysterType type, String description,
				String[] extensions, boolean isArchived, boolean isEnabledByDefault) { 
		commonInit(type, description, extensions, isArchived, isEnabledByDefault);
	}
	
	public TypeDescription(MysterType type, String description) { 
		commonInit(type, description, new String[]{}, false, false);
	}
	
	private void commonInit(MysterType type, String description,
				String[] extensions, boolean isArchived, boolean isEnabledByDefault) {
		this.type				= type;
		this.description		= description;
		this.extensions			= extensions;
		this.isArchived			= isArchived;
		this.isEnabledByDefault	= isEnabledByDefault;
	}
	
	public String getTypeAsString() {
		return type.toString();
	}
	
	public MysterType getType() {
		return type;
	}
	
	public String getDescription() {
		return description;
	}
	
	
	/**
	*	If this array is of length = 0 then simply allow every file
	*	regardless of extension
	*/
	public String[] getExtensions() {
		return (String[])extensions.clone();
	}
	
	public boolean isArchived() {
		return isArchived;
	}
	
	public boolean isEnabledByDefault() {
		return isEnabledByDefault;
	}
	
	public String toString() {
		return new String(type+" "+description);
	}


}

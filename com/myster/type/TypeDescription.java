/* 

	Title:			Myster Open Source
	Author:			Andrew Trumper
	Description:	Generic Myster Code
	
	This code is under GPL

Copyright Andrew Trumper 2000-2001
*/

package com.myster.type;

import java.util.*;
import Myster;
import java.io.InputStream;

public class TypeDescription {
	MysterType type;
	String description;

	public TypeDescription(MysterType t, String d) { commonInit(t,d);}
	
	private void commonInit(MysterType t, String d) {
		type=t;description=d;
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
	
	public String toString() {
		return new String(type+" "+description);
	}


}
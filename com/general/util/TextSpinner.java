/* 

	Title:			Myster Open Source
	Author:			Andrew Trumper
	Description:	Generic Myster Code
	
	This code is under GPL

Copyright Andrew Trumper 2000-2001
*/

package com.general.util;

public class TextSpinner {
	String[] array={"\\","|", "/", "-"};
	RInt counter=new RInt(3);

	public TextSpinner() {}
	
	public String getSpin() {
		return array[counter.inc()];
	}

}
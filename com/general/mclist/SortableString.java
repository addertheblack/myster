/* 
	main.java

	Title:			Multi Column List Package
	Author:			Andrew Trumper
	Description:	A MultiColumn List Package

Copyright Andrew Trumper 2001
*/

package com.general.mclist;




public class SortableString implements Sortable{
	protected String string;
	
	public SortableString(String s) {
		string=s;
	}
	
	public Object getValue() {
		return string;
	}
	
	public boolean isLessThan(Sortable temp) {
		if (temp == this)
	    	return false;
		if (!(temp instanceof SortableString))
	    	return false;
	    String s=(String) temp.getValue();	
	    
	   	if (string.toUpperCase().compareTo(s.toUpperCase())<0) return true;
	   	return false;
	}
	
	public boolean isGreaterThan(Sortable temp) {
		if (temp == this)
	    	return false;
		if (!(temp instanceof SortableString))
	    	return false;
	    String s=(String) temp.getValue();
	    
	   	if (string.toUpperCase().compareTo(s.toUpperCase())>0) return true;
	   	return false;
	}
	
	public boolean equals(Sortable temp) {
		if (temp == this)
	    	return true;
		if (!(temp instanceof SortableString))
	    	return false;
	    String s=(String) temp.getValue();	
	    
	    if (string.toUpperCase().compareTo(s.toUpperCase())==0) return true;
	    return false;
	}
	
	public String toString() {
		return string;
	}
}
/* 
	main.java

	Title:			Multi Column List Package
	Author:			Andrew Trumper
	Description:	A MultiColumn List Package

Copyright Andrew Trumper 2001
*/

package com.general.mclist;

public class SortableBoolean implements Sortable{
	protected boolean bool;
	
	public SortableBoolean(boolean b) {
		bool=b;
	}
	
	public Object getValue() {
		return new Boolean(bool);
	}
	
	public boolean isLessThan(Sortable temp) {
		if (temp == this)
	    	return false;
		if (!(temp instanceof SortableBoolean))
	    	return false;
	    Boolean b=(Boolean) temp.getValue();	
	    
	   	if (bool==false&&b.booleanValue()) return true;
	   	return false;
	}
	
	public boolean isGreaterThan(Sortable temp) {
		if (temp == this)
	    	return false;
		if (!(temp instanceof SortableBoolean))
	    	return false;
	    Boolean b=(Boolean) temp.getValue();
	    
	   	if (bool&&!(b.booleanValue())) return true;
	   	return false;
	}
	
	public boolean equals(Sortable temp) {
		if (temp == this)
	    	return true;
		if (!(temp instanceof SortableBoolean))
	    	return false;
	    Boolean b=(Boolean) temp.getValue();	
	    
	    if (bool==b.booleanValue()) return true;
	    return false;
	}
	
	public String toString() {
		return ""+bool;
	}
}
/* 
	MCList.java

	Title:			Multi Column List Package
	Author:			Andrew Trumper
	Description:	A MultiColumn List Package

Copyright Andrew Trumper 2001
*/

package com.general.mclist;

import com.sun.java.util.collections.Vector;
import com.sun.java.util.collections.Comparator;

public class MCListVector {
	Vector vector; //"no dog food for Vector tonight" -- Paraphrase of Futurama.
	int sortby=0;
	boolean lessthan=true;
	public static final boolean ASENDING=true;
	public static final boolean DESCENDING=false;
	
	protected MCListVector() {
		vector=new Vector(100, 100);
	}
	
    protected synchronized void sort() {
			com.sun.java.util.collections.Collections.sort(
				vector, 
				new Comparator() {
					public int compare(Object a, Object b) {
						Sortable sa = (Sortable)((MCListItemInterface)a).getValueOfColumn(sortby);
						Sortable sb = (Sortable)((MCListItemInterface)b).getValueOfColumn(sortby);
						
						if (sa.equals(sb)) return 0;
						
						int cmp = (sa.isLessThan(sb) ? 1 : -1);
						return (lessthan ? cmp : -cmp);
					}
					
					//Find bugs is complaining about this line. Why is it here? The code below is the
					// default implementation provided by Object class
					//public boolean equals(Object other) { return this == other; }
				});
		}
	
	
	protected synchronized boolean getSortOrder() {
		return lessthan;
	}
	
	protected synchronized boolean reverseSortOrder() {
		return setSortOrder(! lessthan);
	}
	
	protected synchronized boolean setSortOrder(boolean b) {
		if (lessthan != b) {
			lessthan = !lessthan;
			sort();
		}
		
		return lessthan;
	}
	
	protected synchronized void addElement(MCListItemInterface o) {
		vector.addElement(o);
		sort(); //insertion sort if faster but the speed here should never be a bottleneck.
	}
	
	protected synchronized void addElement(MCListItemInterface[] o) {
		vector.ensureCapacity(vector.size()+o.length+1);//the +1 is for kicks...
		for (int i=0; i<o.length; i++) vector.addElement(o[i]);
		sort();
	}
	
	protected synchronized void removeElement(int index) {
		vector.removeElementAt(index);
	}
	
	protected synchronized void removeElement(Object o) {
		vector.removeElement(o);
	}
	
	protected synchronized MCListItemInterface getElement(int index) {
		return (MCListItemInterface)(vector.elementAt(index));
	}
	
	protected synchronized void setSortBy(int i) {
		sortby=i;
		sort();
	}
	
	protected int size() {
		return vector.size();
	}
	
	protected synchronized void removeAllElements() {
		vector.removeAllElements();
	}
	
	protected synchronized void removeIndexes(int[] indexes) {
		MCListItemInterface[] objectsToRemove=new MCListItemInterface[indexes.length];
		
		for (int i=0; i<indexes.length; i++) {
			objectsToRemove[i]=getElement(indexes[i]);
		}
		
		removeElements(objectsToRemove);
	}
	
	protected synchronized void removeElements(MCListItemInterface[] objectsToRemove) {
		for (int i=0; i<objectsToRemove.length; i++) {
			removeElement(objectsToRemove[i]);
		}
	}
	
	protected synchronized int[] getSelectedIndexes() {
		int counter=0;
    	for (int i=0; i<size(); i++) {
    		if (getElement(i).isSelected()) counter++;
    	}
    	
    	int[] temp=new int[counter];
    	
    	int j=0;
    	for (int i=0; i<size(); i++) {
    		if (getElement(i).isSelected()) {
    			temp[j]=i;
    			j++;
    		}
    	}
    	return temp;
    }
    
    protected synchronized int getSelectedIndex() {
    	int workingindex=-1;
    	for (int i=0; i<size(); i++) {
    		if (getElement(i).isSelected()) {
    			if (workingindex==-1) workingindex=i;
    			else return -1;
    		}
    	}
    	return workingindex;
    }

}

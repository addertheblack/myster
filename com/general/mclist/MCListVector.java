/* 
	MCList.java

	Title:			Multi Column List Package
	Author:			Andrew Trumper
	Description:	A MultiColumn List Package

Copyright Andrew Trumper 2001
*/

package com.general.mclist;

import com.sun.java.util.collections.Vector;
import com.sun.java.util.collections.Collections;
import com.sun.java.util.collections.Comparator;

public class MCListVector{
	Vector vector; //"no dog food for Vector tonight" -- Paraphrase of Futurama.
	int sortby=0;
	boolean lessthan=true;
	public static final boolean ASENDING=true;
	public static final boolean DESCENDING=false;
	
	protected MCListVector() {
		vector=new Vector(100, 100);
	}
	
	protected synchronized void insertionSort() {
		for(int i=0; i<vector.size()-1; i++) {
			for (int j=i+1; j>0 ; j--) {
				if (isLessThan(getRelevent(j), getRelevent(j-1))) {
					swap(j, j-1);
				} else {
					break;
				}
			} 
		}
	}
	
	
	
	private synchronized void quickSort(int l, int r) {
        int M = 4;
        int i;
        int j;
        Sortable  v;

        if ((r-l)>M)
        {
                i = (r+l)/2;
                
                if (isGreaterThan(getRelevent(l), getRelevent(i))) swap(l,i);     // Tri-Median Methode!
                if (isGreaterThan(getRelevent(l), getRelevent(r))) swap(l,r);
                if (isGreaterThan(getRelevent(i), getRelevent(r))) swap(i,r);

                j = r-1;
                swap(i,j);
                i = l;
                v = getRelevent(j);
                for(;;) {
                    while(isLessThan(getRelevent(++i), v));
                    while(isGreaterThan(getRelevent(--j), v));
					if (j<i) break;
					swap (i,j);
				}
				swap(i,r-1);
				quickSort(l,j);
				quickSort(i+1,r);
			}
    }
    
    private synchronized boolean isGreaterThan(Sortable a, Sortable b){
    	if (lessthan) return a.isGreaterThan(b);
    	else return a.isLessThan(b);
    }
    
    private synchronized boolean isLessThan(Sortable a, Sortable b){
    	if (!lessthan) return a.isGreaterThan(b);
    	else return a.isLessThan(b);
    }
    
    private synchronized Sortable getRelevent(int i) {
    	return getElement(i).getValueOfColumn(sortby);
    }
    
    protected synchronized void sort() {
			//quickSort(0, vector.size() - 1);
			//insertionSort();
			com.sun.java.util.collections.Collections.sort(
				vector, 
				new Comparator() {
					public int compare(Object a, Object b) {
						Sortable sa = (Sortable)((MCListItemInterface)a).getValueOfColumn(sortby);
						Sortable sb = (Sortable)((MCListItemInterface)b).getValueOfColumn(sortby);
						if (sa.equals(sb)) return 0;
						int cmp = (sa.isLessThan(sb) ? 1 : -1);
						if (lessthan) return cmp;
						else return -cmp;
					}
					public boolean equals(Object other) { return this == other; }
				});
		}
	
	
	protected synchronized boolean getSortOrder() {
		return lessthan;
	}
	
	protected synchronized void reverseSortOrder() {
		sort();
		lessthan=!lessthan;
		int s=size();
    	for (int i=0; (s-i-1)>i; i++) {
    		swap(i, s-i-1);
    	}
	}
	
	protected synchronized void setSortOrder(boolean b) {
		if (lessthan!=b) reverseSortOrder();
	}
	
	protected synchronized void addElement(MCListItemInterface o) {
		vector.addElement(o);
		insertionSort();
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
	
	protected synchronized void swap(int index1, int index2) {
		Object temp;
		
		temp=vector.elementAt(index1);
		vector.setElementAt(vector.elementAt(index2), index1);
		vector.setElementAt(temp, index2);
	}
	

}

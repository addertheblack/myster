/* 
	main.java

	Title:			Multi Column List Package
	Author:			Andrew Trumper
	Description:	A MultiColumn List Package

Copyright Andrew Trumper 2001
*/

package com.general.mclist;

public class RowStats {
	int numberofcolumns;
	int[]columnwidtharray;
	int padding;
	
	public RowStats(int[] columnwidtharray, int padding) {
		this.numberofcolumns=columnwidtharray.length;
		this.columnwidtharray=columnwidtharray;
		this.padding=padding;
	}
	
	public int getNumberOfColumns() {
		return numberofcolumns;
	}
	
	public int getWidthOfColumn(int x) {
		return columnwidtharray[x];
	}
	
	public int getTotalWidthOfColunm(int x) {
		return getWidthOfColumn(x)+padding;
	}
	
	public int getPadding() {
		return padding;
	}
	
	public int getTotalLength() {
		int count=0;
		for (int i=0;i<columnwidtharray.length; i++){
			count+=columnwidtharray[i];
		}
		count+=padding*columnwidtharray.length+padding; //padding calcs
		return count;
	}
}
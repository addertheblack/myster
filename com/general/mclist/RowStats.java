/*
 * main.java
 * 
 * Title: Multi Column List Package Author: Andrew Trumper Description: A
 * MultiColumn List Package
 * 
 * Copyright Andrew Trumper 2001
 */

package com.general.mclist;

public class RowStats {
    private int numberofcolumns;

    private int[] columnwidtharray;

    private int padding;

    private int internalPadding;

    public RowStats(int[] columnwidtharray, int padding, int internalPadding) {
        this.numberofcolumns = columnwidtharray.length;
        this.columnwidtharray = columnwidtharray;
        this.padding = padding;
        this.internalPadding = internalPadding;
    }

    public int getNumberOfColumns() {
        return numberofcolumns;
    }

    public int getWidthOfColumn(int x) {
        return columnwidtharray[x];
    }

    public int getTotalWidthOfColunm(int x) {
        return getWidthOfColumn(x) + padding + 2*internalPadding;
    }

    public int getPadding() {
        return padding;
    }
    
    public int getInternalPadding() {
        return internalPadding;
    }

    public int getTotalLength() {
        int count = 0;
        for (int i = 0; i < columnwidtharray.length; i++) {
            count += columnwidtharray[i];
        }
        count += (2*internalPadding) * padding * columnwidtharray.length + padding; //padding calcs
        return count;
    }
}
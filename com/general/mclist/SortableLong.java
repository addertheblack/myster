/*
 * main.java
 * 
 * Title: Multi Column List Package Author: Andrew Trumper Description: A
 * MultiColumn List Package
 * 
 * Copyright Andrew Trumper 2001
 */

package com.general.mclist;

public class SortableLong implements Sortable {
    protected long number;

    public SortableLong(long n) {
        number = n;
    }

    public Object getValue() {
        return new Long(number);
    }

    public boolean isLessThan(Sortable temp) {
        if (temp == this)
            return false;
        if (!(temp instanceof SortableLong))
            return false;
        Long n = (Long) temp.getValue();

        if (number < n.longValue())
            return true;
        return false;
    }

    public boolean isGreaterThan(Sortable temp) {
        if (temp == this)
            return false;
        if (!(temp instanceof SortableLong))
            return false;
        Long n = (Long) temp.getValue();

        if (number > n.longValue())
            return true;
        return false;
    }

    public boolean equals(Sortable temp) {
        if (temp == this)
            return true;
        if (!(temp instanceof SortableLong))
            return false;
        Long n = (Long) temp.getValue();

        if (number == n.longValue())
            return true;
        return false;
    }

    public String toString() {
        return "" + number;
    }
}
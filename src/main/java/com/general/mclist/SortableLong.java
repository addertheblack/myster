/*
 * main.java
 * 
 * Title: Multi Column List Package Author: Andrew Trumper Description: A
 * MultiColumn List Package
 * 
 * Copyright Andrew Trumper 2001
 */

package com.general.mclist;

public class SortableLong implements Sortable<Long> {
    protected long number;

    public SortableLong(long n) {
        number = n;
    }

    public Long getValue() {
        return number;
    }

    public boolean isLessThan(Sortable<Long> temp) {
        if (temp == this)
            return false;
        if (!(temp instanceof SortableLong))
            return false;
        Long n = temp.getValue();

        if (number < n.longValue())
            return true;
        return false;
    }

    public boolean isGreaterThan(Sortable<Long> temp) {
        if (temp == this)
            return false;
        if (!(temp instanceof SortableLong))
            return false;
        Long n = temp.getValue();

        if (number > n.longValue())
            return true;
        return false;
    }

    public boolean equals(Object temp) {
        if (temp == this)
            return true;
        if (!(temp instanceof SortableLong))
            return false;
        @SuppressWarnings("cast")
        Long n = (Long) ((SortableLong)temp).getValue();

        if (number == n.longValue())
            return true;
        return false;
    }

    public String toString() {
        return "" + number;
    }
}
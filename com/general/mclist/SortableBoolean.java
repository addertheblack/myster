/*
 * main.java
 * 
 * Title: Multi Column List Package Author: Andrew Trumper Description: A
 * MultiColumn List Package
 * 
 * Copyright Andrew Trumper 2001
 */

package com.general.mclist;

public class SortableBoolean implements Sortable {
    protected boolean bool;

    public SortableBoolean(boolean b) {
        bool = b;
    }

    public Object getValue() {
        return (bool ? Boolean.TRUE : Boolean.FALSE);
    }

    public boolean isLessThan(Sortable temp) {
        if (!(temp instanceof SortableBoolean))
            return false;

        return (!bool && ((SortableBoolean) temp).bool);
    }

    public boolean isGreaterThan(Sortable temp) {
        if (!(temp instanceof SortableBoolean))
            return false;

        return (bool && !((SortableBoolean) temp).bool);
    }

    public boolean equals(Sortable temp) {
        if (temp == this)
            return true;
        if (!(temp instanceof SortableBoolean))
            return false;

        return (bool == ((SortableBoolean) temp).bool);
    }

    public String toString() {
        return "" + bool;
    }
}
/*
 * main.java
 * 
 * Title: Multi Column List Package Author: Andrew Trumper Description: A
 * MultiColumn List Package
 * 
 * Copyright Andrew Trumper 2001
 */

package com.general.mclist;

import java.text.Collator;

public class SortableString implements Sortable {
    protected String string;

    private static final Collator collator = Collator.getInstance();

    public SortableString(String s) {
        string = s;
    }

    public Object getValue() {
        return string;
    }

    public boolean isLessThan(Sortable temp) {
        if (temp == this)
            return false;
        if (!(temp instanceof SortableString))
            return false;
        String s = (String) temp.getValue();

        return collator.compare(string, s) < 0;
        //return collator(string.toUpperCase(),s.toUpperCase())<0;
    }

    public boolean isGreaterThan(Sortable temp) {
        if (temp == this)
            return false;
        if (!(temp instanceof SortableString))
            return false;
        String s = (String) temp.getValue();

        return collator.compare(string, s) > 0;
        //return collator(string.toUpperCase(),s.toUpperCase())>0;
    }

    public boolean equals(Sortable temp) {
        if (temp == this)
            return true;
        if (!(temp instanceof SortableString))
            return false;
        String s = (String) temp.getValue();

        return collator.compare(string, s) == 0;
        //return collator(string.toUpperCase(),s.toUpperCase())==0;
    }

    public String toString() {
        return string;
    }
}
/*
 * MCList.java
 * 
 * Title: Multi Column List Package Author: Andrew Trumper Description: A
 * MultiColumn List Package
 * 
 * Copyright Andrew Trumper 2001
 */

package com.general.mclist;

public interface Sortable<T> {
    public boolean isLessThan(Sortable<T> m);

    public boolean isGreaterThan(Sortable<T> m);

    public boolean equals(Sortable<T> m);

    public T getValue();
}
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
    public boolean isLessThan(Sortable<?> m);

    public boolean isGreaterThan(Sortable<?> m);

    public boolean equals(Object m);

    public T getValue();
}
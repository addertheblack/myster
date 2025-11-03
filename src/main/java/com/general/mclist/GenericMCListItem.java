/*
 * MCList.java
 * 
 * Title: Multi Column List Package Author: Andrew Trumper Description: A
 * MultiColumn List Package
 * 
 * Copyright Andrew Trumper 2001
 */

package com.general.mclist;

public class GenericMCListItem<E> extends AbstractMCListItemInterface<E> {
    protected Sortable<?>[] sortables;

    protected E object;

    public GenericMCListItem(Sortable<?>[] s) {
        this(s, null);
    }

    public GenericMCListItem(Sortable<?>[] s, E o) {
        sortables = s;
        object = o;
    }

    public Sortable<?> getValueOfColumn(int i) {
        //if (i>=sortables.length||i<0) return "ERR";
        return sortables[i];
    }

    public E getObject() {
        return object;
    }
}
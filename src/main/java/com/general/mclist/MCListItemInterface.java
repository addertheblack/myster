/*
 * MCList.java
 * 
 * Title: Multi Column List Package Author: Andrew Trumper Description: A
 * MultiColumn List Package
 * 
 * Copyright Andrew Trumper 2001
 */

package com.general.mclist;

public abstract class MCListItemInterface<T> {
    boolean selected = false;

    public abstract Sortable<?> getValueOfColumn(int i);

    public T getObject() {
        return null;
    }

    public final void setSelected(boolean b) {
        selected = b;
    }

    public final boolean isSelected() {
        return selected;
    }

    protected final void toggleSelection() {
        if (selected)
            selected = false;
        else
            selected = true;
    }
}

/*
 * MCList.java
 * 
 * Title: Multi Column List Package Author: Andrew Trumper Description: A
 * MultiColumn List Package
 * 
 * Copyright Andrew Trumper 2001
 */

package com.general.mclist;

public class MCListEvent {
    private final MCList<?> parent;

    public MCListEvent(MCList<?> parent) {
        this.parent = parent;
    }

    public MCList<?> getParent() {
        return parent;
    }
}
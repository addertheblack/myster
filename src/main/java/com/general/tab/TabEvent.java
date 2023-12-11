/*
 * Main.java
 * 
 * Title: Server Stats Window Test App Author: Andrew Trumper Description: An
 * app to test the server stats window
 */

package com.general.tab;

import java.util.EventObject;

public class TabEvent extends EventObject {
    private int tabid = -1;

    public TabEvent(TabPanel parent, int tabid) {
        super(parent); //doggon super parent!
        this.tabid = tabid;
    }

    public int getTabID() {
        return tabid;
    }
}
/*
 * Main.java
 * 
 * Title: Server Stats Window Test App Author: Andrew Trumper Description: An
 * app to test the server stats window
 */

package com.myster.server.ui;

import java.awt.List;

public class XItemList extends List {
    int maxItems = 0;

    int itemCounter = 0;

    public XItemList(int max) {
        super(max + 1);
        maxItems = max;
    }

    public void add(String s) {
        addItem(s);
    }

    public synchronized void addItem(String s) { //two threads in here at once
                                                 // would suck.
        if (itemCounter < maxItems) {
            add(s, 0);
            itemCounter++;
        } else {
            remove(maxItems - 1);
            add(s, 0);

        }
    }

}
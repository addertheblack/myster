/*
 * Main.java
 * 
 * Title: Server Stats Window Test App Author: Andrew Trumper Description: An
 * app to test the server stats window
 */

package com.myster.server.ui;

import javax.swing.DefaultListModel;
import javax.swing.JList;

public class XItemList extends JList {
    private DefaultListModel model = new DefaultListModel();
    private int maxItems = 0;

    public XItemList(int max) {
        super();
        maxItems = max;
        setModel( model);
    }

    public void add(String s) {
        addItem(s);
    }

    public synchronized void addItem(String s) { //two threads in here at once
                                                 // would suck.
        model.addElement(s);
        if (model.size() >= maxItems) {
            model.removeElementAt(0);
        }
    }
}
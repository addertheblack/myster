/*
 * 
 * Title: Myster Open Source Author: Andrew Trumper Description: Generic Myster
 * Code
 * 
 * This code is under GPL
 * 
 * Copyright Andrew Trumper 2000-2001
 */

package com.myster.ui.menubar.event;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import com.myster.search.ui.SearchWindow;
import com.myster.ui.MysterFrameContext;

public class NewSearchWindowAction implements ActionListener {
    private final MysterFrameContext context;

    public NewSearchWindowAction(MysterFrameContext context) {
        this.context = context;
    }

    public void actionPerformed(ActionEvent e) {
        (new SearchWindow(context)).setVisible(true);
    }

}
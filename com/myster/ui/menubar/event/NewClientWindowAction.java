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

import com.myster.client.ui.ClientWindow;
import com.myster.ui.MysterFrameContext;

public class NewClientWindowAction implements ActionListener {
    private final MysterFrameContext context;

    public NewClientWindowAction(MysterFrameContext context) {
        this.context = context;

    }

    public void actionPerformed(ActionEvent e) {
        ClientWindow w = new ClientWindow(context);

        w.show();
    }

}
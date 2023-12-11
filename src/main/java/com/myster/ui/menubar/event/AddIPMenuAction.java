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

import com.myster.tracker.IPListManager;
import com.myster.tracker.ui.AddIPDialog;

public class AddIPMenuAction implements ActionListener {
    private final IPListManager ipListManager;
    
    public AddIPMenuAction(IPListManager ipListManager) {
        this.ipListManager = ipListManager;
    }
    
    public void actionPerformed(ActionEvent e) {
        AddIPDialog a = new AddIPDialog(ipListManager);
        a.show();
    }

}
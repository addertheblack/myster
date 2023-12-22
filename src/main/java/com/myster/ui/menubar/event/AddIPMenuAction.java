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

import com.myster.tracker.IpListManager;
import com.myster.tracker.ui.AddIPDialog;
import com.myster.ui.WindowManager;

public class AddIPMenuAction implements ActionListener {
    private final IpListManager ipListManager;
    private final WindowManager windowManager;
    
    public AddIPMenuAction(IpListManager ipListManager, WindowManager windowManager) {
        this.ipListManager = ipListManager;
        this.windowManager = windowManager;
    }
    
    public void actionPerformed(ActionEvent e) {
        AddIPDialog a = new AddIPDialog(ipListManager, windowManager);
        a.show();
    }

}
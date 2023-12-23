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
import java.net.UnknownHostException;

import com.general.util.AskDialog;
import com.myster.net.MysterAddress;
import com.myster.tracker.IpListManager;
import com.myster.ui.WindowManager;

public class AddIPMenuAction implements ActionListener {
    private final IpListManager ipListManager;
    private final WindowManager windowManager;
    
    public AddIPMenuAction(IpListManager ipListManager, WindowManager windowManager) {
        this.ipListManager = ipListManager;
        this.windowManager = windowManager;
    }
    
    public void actionPerformed(ActionEvent e) {
        String answer = AskDialog.simpleAsk("What server address would you like me to check?");
        if (answer == null) {
            return;
        }
        
        try {
            ipListManager.addIP(
                    new MysterAddress(answer));
        } catch (UnknownHostException ex) {
            System.out.println("The \"Name\" : " + answer
                    + " is not a valid domain name at all!");
        }
//        AddIPDialog a = new AddIPDialog(ipListManager, windowManager);
//        a.show();
    }

}
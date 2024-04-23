/*
 * Title: Myster Open Source Author: Andrew Trumper 
 * Description: Generic Myster Code
 * 
 * This code is under GPL
 * 
 * Copyright Andrew Trumper 2000-2001
 */

package com.myster.ui.menubar.event;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.UnknownHostException;
import java.util.logging.Logger;

import com.general.util.AskDialog;
import com.myster.net.MysterAddress;
import com.myster.tracker.MysterServerManager;

public class AddIPMenuAction implements ActionListener {
    private static final Logger LOGGER = Logger.getLogger(AddIPMenuAction.class.getName());
    
    private final MysterServerManager ipListManager;
    
    public AddIPMenuAction(MysterServerManager ipListManager) {
        this.ipListManager = ipListManager;
    }
    
    public void actionPerformed(ActionEvent e) {
        String answer = AskDialog.simpleAsk("What server address would you like me to check?");
        if (answer == null) {
            return;
        }

        try {
            ipListManager.addIp(new MysterAddress(answer));
        } catch (UnknownHostException ex) {
            LOGGER.info("The \"Name\" : " + answer + " is not a valid domain name at all!");
        }
    }
}
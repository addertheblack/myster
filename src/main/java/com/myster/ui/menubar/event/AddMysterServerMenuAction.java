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
import com.myster.tracker.Tracker;

public class AddMysterServerMenuAction implements ActionListener {
    private static final Logger log = Logger.getLogger(AddMysterServerMenuAction.class.getName());
    
    private final Tracker tracker;
    
    public AddMysterServerMenuAction(Tracker tracker) {
        this.tracker = tracker;
    }
    
    public void actionPerformed(ActionEvent e) {
        String answer = AskDialog.simpleAsk("What server address would you like me to check?");
        if (answer == null) {
            return;
        }

        try {
            tracker.addIp(MysterAddress.createMysterAddress(answer));
        } catch (UnknownHostException ex) {
            log.info("The \"Name\" : " + answer + " is not a valid domain name at all!");
        }
    }
}
/*
 * 
 * Title: Myster Open Source Author: Andrew Trumper Description: Generic Myster
 * Code
 * 
 * This code is under GPL
 * 
 * Copyright Andrew Trumper 2000-2001
 */

package com.myster.menubar.event;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import com.myster.client.ui.ClientWindow;

public class NewClientWindowAction implements ActionListener {

    public void actionPerformed(ActionEvent e) {
        ClientWindow w = new ClientWindow();
        w.show();
    }

}
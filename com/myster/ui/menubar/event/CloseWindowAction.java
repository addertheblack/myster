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

public class CloseWindowAction implements ActionListener {
    public void actionPerformed(ActionEvent e) {
        com.myster.ui.WindowManager.getFrontMostWindow().closeWindowEvent();
    }

}
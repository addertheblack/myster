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

import com.myster.tracker.ui.TrackerWindow;

public class TrackerWindowAction implements ActionListener {

    public void actionPerformed(ActionEvent e) {
        TrackerWindow.getInstance().setVisible(true);
        TrackerWindow.getInstance().toFrontAndUnminimize();
        TrackerWindow.getInstance().setEnabled(true);
    }

}
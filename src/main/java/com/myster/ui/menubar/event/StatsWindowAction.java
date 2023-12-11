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

import com.myster.server.ui.ServerStatsWindow;

public class StatsWindowAction implements ActionListener {

    public void actionPerformed(ActionEvent e) {
        ServerStatsWindow.getInstance().setVisible(true);
        ServerStatsWindow.getInstance().toFrontAndUnminimize();
    }

}
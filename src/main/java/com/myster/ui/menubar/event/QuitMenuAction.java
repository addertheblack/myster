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

//import Myster;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class QuitMenuAction implements ActionListener {

    public void actionPerformed(ActionEvent e) {
        com.myster.application.MysterGlobals.quit();//System.exit(0);
    }

}
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

import com.myster.pref.Preferences;

public class PreferencesAction implements ActionListener {

    public void actionPerformed(ActionEvent e) {
        Preferences.getInstance().setGUI(true);
    }

}
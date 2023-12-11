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

import com.myster.ui.PreferencesGui;

public class PreferencesAction implements ActionListener {
    private final PreferencesGui prefGui;
    
    public PreferencesAction(PreferencesGui prefGui) {
        if (prefGui == null)
            throw new NullPointerException("prefGui is null");
        this.prefGui = prefGui;
    }

    public void actionPerformed(ActionEvent e) {
        prefGui.setGUI(true);
    }

}
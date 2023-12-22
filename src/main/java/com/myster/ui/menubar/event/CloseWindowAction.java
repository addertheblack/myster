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

import javax.swing.AbstractAction;

import com.myster.ui.WindowManager;

public class CloseWindowAction extends AbstractAction {
    private final WindowManager windowManager;

    public CloseWindowAction(String title, WindowManager windowManager) {
        super(title);
        this.windowManager = windowManager;

        windowManager.addWindowListener((count) -> setEnabled(count != 0));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        windowManager.getFrontMostWindow().closeWindowEvent();
    }
}
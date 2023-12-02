/*
 * 
 * Title: Myster Open Source Author: Andrew Trumper Description: Generic Myster
 * Code
 * 
 * This code is under GPL
 * 
 * Copyright Andrew Trumper 2000-2001
 */

package com.myster.util;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import com.general.thread.SafeThread;

public class ProgressWindowClose extends WindowAdapter {
    SafeThread t;

    public ProgressWindowClose(SafeThread t) {
        this.t = t;
    }

    public void windowClosing(WindowEvent e) {
        try {
            // I think you can't end() here because doing that blocks the EDT
            // Best to not do that.
            t.flagToEnd();
        } catch (Exception ex) {
            // nothing
        }
        e.getWindow().setVisible(false);
        e.getWindow().dispose();
    }

}
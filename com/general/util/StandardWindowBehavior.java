/*
 * 
 * Title: Myster Open Source Author: Andrew Trumper Description: Generic Myster
 * Code
 * 
 * This code is under GPL
 * 
 * Copyright Andrew Trumper 2000-2001
 */

package com.general.util;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class StandardWindowBehavior extends WindowAdapter {
    public void windowClosing(WindowEvent e) {
        e.getWindow().setVisible(false);
    }

    public void windowClosed(WindowEvent e) {
        e.getWindow().dispose();
    }
}
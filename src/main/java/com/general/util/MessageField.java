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

import javax.swing.JLabel;

public class MessageField extends JLabel {

    public MessageField(String s) {
        say(s);
    }

    public void say(String s) {
        setText("Status: " + s);
    }
}
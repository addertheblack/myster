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

import java.awt.Label;

public class MessageField extends Label {

    public MessageField(String s) {
        say(s);
    }

    public void say(String s) {
        setText("Status: " + s);
    }
}
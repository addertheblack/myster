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

import java.awt.TextField;

public class MessageField extends TextField {

    public MessageField(String s) {
        super(s);
    }

    public void say(String s) {
        setText(s);
    }
}
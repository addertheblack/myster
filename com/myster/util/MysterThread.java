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

import com.general.util.SafeThread;

/**
 * This class
 * 
 * @author Andrew Trumper
 *
 */

public abstract class MysterThread extends SafeThread {

    public MysterThread() {
        setPriority(Thread.MIN_PRIORITY);
    }

    public MysterThread(String s) {
        super(s);
        setPriority(Thread.MIN_PRIORITY);
    }

    public void end() {
        stop();
    }

}
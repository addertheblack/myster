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
 * This class is the subclass of all threads created by Myster. The class is basically identical to
 * a safe thread exception that the end() routine has a default implementation of calling stop()
 * (bad) and it sets itself to minimum priority.
 * 
 * 
 * @author Andrew Trumper
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
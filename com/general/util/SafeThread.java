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

/**
 * This class over-rides thread and adds some different, basic functionality
 * that is missing from a regular thread. Since the stop() method is deprecated
 * we need a safe method to stop a thread. This method is called "end". However
 * There are multiple cases where this method will take a long time to finish so
 * we also include a falgToEnd() method. end() simply calls flagToEnd then joins
 * on the thread.
 * 
 * @author Andrew Trumper
 */

public abstract class SafeThread extends Thread {
    protected boolean endFlag = false;

    public SafeThread() { //being explicit is good.
    }

    public SafeThread(String name) {
        super(name);
    }

    public SafeThread(Runnable runnable) {
        super(runnable);
    }

    public void flagToEnd() {
        endFlag = true;
    }

    public void end() {
        flagToEnd();
        try {
            join();
        } catch (InterruptedException ex) {
        }//I hope this doens't come back and bite me. - it will...
    }

    public boolean isFlaggedToEnd() {
        return endFlag;
    }
}
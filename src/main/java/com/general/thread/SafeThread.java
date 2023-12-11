/*
 * 
 * Title: Myster Open Source Author: Andrew Trumper Description: Generic Myster
 * Code
 * 
 * This code is under GPL
 * 
 * Copyright Andrew Trumper 2000-2001
 */

package com.general.thread;

import java.util.concurrent.CancellationException;

/**
 * This class over-rides thread and adds some different, basic functionality that is missing from a
 * regular thread. Since the stop() method is deprecated we need a safe method to stop a thread.
 * This method is called "end". However There are multiple cases where this method will take a long
 * time to finish so we also include a flagToEnd() method. end() simply calls flagToEnd then joins
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

    /**
     * Ends the thread as soon as possible. Does not block, returns immediately, irrespective of the
     * state of the thread.
     */
    public void flagToEnd() {
        endFlag = true;
    }

    /**
     * A safe version of stop(). Ends the thread a soon as possible. Blocks until the thread has
     * died. Usually implemented by calling flagToEnd() then joining on the thread.
     */
    public void end() {
        flagToEnd();
        try {
            join();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new CancellationException();
        }//I hope this doens't come back and bite me. - it will... It has..
    }

    /**
     * returns whether or not flagToEnd() has been called on this thread object.
     * 
     * @return true if the thread has been told to end.
     */
    public boolean isFlaggedToEnd() {
        return endFlag;
    }
}
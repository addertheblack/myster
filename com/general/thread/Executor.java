/*
 * Created on Oct 23, 2004
 * by Andrew Trumper
 */
package com.general.thread;

/**
 * @author Andrew Trumper
 */
public interface Executor {
    /**
     * 
     * NOTE: The relationship between all the fields of the Future and the
     * CallListener is the following (from viewed from all threads).
     * 
     * <ol>
     * <li>isCancelled() is set to true (if it was cancelled).</li>
     * <li>cancel() is called on the CancellableCallable.</li>
     * <li>isDone is set to true in the Future.</li>
     * <li>Only one of handleResult(), handleException() or handleCancel() is
     * called in the CallListener.</li>
     * <li>handleFinally() is called.</li>
     * </ol>
     * 
     * Also note that the listener's routines are called on the event thread for
     * your convenience.
     * 
     * @param callable
     * @param listener
     * @return
     */
    public Future execute(CancellableCallable callable, CallListener listener);
}
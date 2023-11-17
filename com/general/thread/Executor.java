/*
 * Created on Oct 23, 2004
 * by Andrew Trumper
 */
package com.general.thread;

/**
 * Interface generally implemented by objects wanting to implement an asynchronous function call.
 * 
 * @see com.general.thread.CancellableCallable
 * @see com.general.thread.Executor
 * @see com.general.thread.Future
 */
public interface Executor {
    /**
     * 
     * This routine should execute the CancellableCallable and call back the CallListener while
     * allowing the client to control the progress of the call through the Future object returned.
     * <p>
     * <b>
     * NOTE: The relationship between all the fields of the Future and the CallListener is the
     * following (from viewed from all threads).
     * </b>
     * <p>
     * <ol>
     * <li>isCancelled() is set to true (if it was cancelled).</li>
     * <li>cancel() is called on the CancellableCallable.</li>
     * <li>isDone is set to true in the Future.</li>
     * <li>Only one of handleResult(), handleException() or handleCancel() is called in the
     * CallListener.</li>
     * <li>handleFinally() is called.</li>
     * </ol>
     * <p>
     * Also note that the listener's routines are called on the event thread (unless otherwise
     * specified) for your convenience.
     */
    public <T> Future<T> execute(CancellableCallable<T> callable, CallListener<T> listener);
}
/*
 * Created on Oct 23, 2004
 * by Andrew Trumper
 */
package com.general.thread;

import java.util.concurrent.Callable;

/**
 * Similar to the Callable class in java 1.5. This callable here can be cancelled. THis is for use
 * with the Executor and future in this package.
 * 
 * @see com.general.thread.CancellableCallable
 * @see com.general.thread.Executor
 * @see com.general.thread.Future
 */
public interface CancellableCallable<T> extends Cancellable, Callable<T> {
    /**
     * This function is called by Executors by way of Futures to signal this task to stop Execution
     * as soon as possible.
     */
    public void cancel();
}
/*
 * Created on Oct 23, 2004
 * by Andrew Trumper
 */
package com.general.thread;

/**
 * Similar to the Callable class in java 1.5. This callable here can be cancelled. THis is for use
 * with the Executor and future in this package.
 * <p>
 * Class that implements the callable interface in 1.5, but 1.1 (1.0 really) compatible.
 * 
 * @see com.general.thread.CancellableCallable
 * @see com.general.thread.Executor
 * @see com.general.thread.Future
 */
public interface CancellableCallable extends Cancellable {
    /**
     * Should be over-ridden by classes wanting to implement a function to be used asynchronously.
     * 
     * @return the result
     * @throws Exception
     */
    public Object call() throws Exception;

    /**
     * This function is called by Executors by way of Futures to signal this task to stop Execution
     * as soon as possible.
     */
    public void cancel();
}
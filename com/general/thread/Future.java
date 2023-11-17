/*
 * Created on Oct 23, 2004
 * by Andrew Trumper
 */
package com.general.thread;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;


/**
 * The Future object is a mechanism to allow client code to control or communicate with a task
 * executing asynchronously.
 * 
 * @see com.general.thread.CancellableCallable
 * @see com.general.thread.Executor
 * @see com.general.thread.Future
 */
public interface Future<T> extends java.util.concurrent.Future<T>{
    public T get() throws InterruptedException, ExecutionException, CancellationException;
    
    /**
     * Cancel will attempt to cancel the asynchronous task represented by this object without
     * calling interrupt() on the thread.
     * 
     * @return true is task could be cancelled, false otherwise. false may be returned if the task
     *         had already been cancelled, for example
     */
    public boolean cancel();

    /**
     * Cancel will attempt to cancel the asynchronous task represented by this object.
     * 
     * @return true is task could be cancelled, false otherwise. false may be returned if the task
     *         had already been cancelled, for example
     */
    public boolean cancel(boolean mayInterruptIfRunning);

    /**
     * Returns the cancel status of this task.
     * 
     * @return true if the task has been cancelled().
     */
    public boolean isCancelled();

    /**
     * Returns the done status of this task.
     * 
     * @return true if the asynchronous task has been completed.
     */
    public boolean isDone();
}
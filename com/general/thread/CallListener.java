/*
 * Created on Oct 23, 2004
 * by Andrew Trumper
 * 
 * This code is under GPL.
 */
package com.general.thread;

/**
 * This interface is used by clients who wish to make an asynchronous function call.
 * <p>
 * This interface is made to be used with CancelableCallable, Executors and Futures.
 * <p>
 * Only one of handleCancel(), handleResult() and handleException() will be called, followed by
 * handleFinally().
 * <p>
 * Throwing an error causes undefined behavior.
 * <p>
 * Both calls should be on the same thread. Calls should be on the event thread however it i up to
 * the Executor to decide which thread to dispatch these calls on. Unless otherwise specified it
 * should be assumed that the callback will happen on the event thread.
 * <p>
 * 
 * @see com.general.thread.CancellableCallable
 * @see com.general.thread.Executor
 * @see com.general.thread.Future
 */
public interface CallListener {
    /**
     * is called when the CancellableCallable is cancelled.
     */
    public void handleCancel();

    /**
     * is called when the CancellableCallable returns successfully.
     * 
     * @param result
     *            the result returned by the CancellableCallable
     */
    public void handleResult(Object result);

    /**
     * is called when the cancellable callable throws an Exception. Currently does nto work with
     * Errors.
     * 
     * @param ex
     *            Exception thrown by the CancellableCallable
     */
    public void handleException(Exception ex);

    /**
     * is called after either handleCancel(), handleResult() or handleException().
     */
    public void handleFinally();
}
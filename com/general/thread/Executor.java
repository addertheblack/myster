/*
 * Created on Oct 23, 2004
 * by Andrew Trumper
*/
package com.general.thread;

/**
 * @author Andrew Trumper
 */
public interface Executor {
    public Future execute(CancellableCallable callable, CallListener listener);
}

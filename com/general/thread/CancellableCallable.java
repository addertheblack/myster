/*
 * Created on Oct 23, 2004
 * by Andrew Trumper
 */
package com.general.thread;

/**
 * Class that implements the callable interace in 1.5, but 1.1 (1.0 really)
 * compatible.
 * 
 * @author Andrew Trumper - 2004
 */
public interface CancellableCallable {
    public Object call() throws Exception;

    public void cancel();
}
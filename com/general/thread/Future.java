/*
 * Created on Oct 23, 2004
 * by Andrew Trumper
*/
package com.general.thread;

/**
 * @author Andrew Trumper
 */
public interface Future {
    public boolean cancel();
    public boolean cancel(boolean mayInterruptIfRunning);
    public boolean isCancelled();
    public boolean isDone() ;
}

/*
 * Created on Oct 23, 2004
 * by Andrew Trumper
*/
package com.general.thread;

/**
 * @author Andrew Trumper
 */
public interface CallListener {
    public void handleCancel();
    public void handleResult(Object result);
    public void handleException(Exception ex);
    public void handleFinally();
}

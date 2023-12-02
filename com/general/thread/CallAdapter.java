package com.general.thread;

/**
 * CallAdapter makes it easier to over-ride only one or two of the methods in CallListener instead
 * of every freakin' one. This pattern is in many places in java's awt event system.
 * 
 * @see java.awt.event.WindowAdapter
 */
public class CallAdapter<T> implements CallListener<T> {

    /*
     * (non-Javadoc)
     * 
     * @see com.general.thread.CallListener#handleCancel()
     */
    public void handleCancel() {
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.general.thread.CallListener#handleResult(java.lang.Object)
     */
    public void handleResult(T result) {
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.general.thread.CallListener#handleException(java.lang.Exception)
     */
    public void handleException(Exception ex) {
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.general.thread.CallListener#handleFinally()
     */
    public void handleFinally() {
    }

}
package com.myster.net;

import java.io.IOException;

/**
 * USe this class to indicate that your protocol has timed out waiting for a response.
 */
public class TimeoutException extends IOException {
    /**
     * Constructs an <code>IOException</code> with <code>null</code> as its
     * error detail message.
     */
    public TimeoutException() {
        super();
    }

    /**
     * Constructs an <code>IOException</code> with the specified detail
     * message. The error message string <code>s</code> can later be retrieved
     * by the <code>{@link java.lang.Throwable#getMessage}</code> method of
     * class <code>java.lang.Throwable</code>.
     * 
     * @param s
     *            the detail message.
     */
    public TimeoutException(String s) {
        super(s);
    }
}
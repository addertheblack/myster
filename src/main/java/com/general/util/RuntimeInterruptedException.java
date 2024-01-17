
package com.general.util;

/**
 * Like InterruptedException but as a runtime exception
 * Throwing this AUTOMATICALLY RE INTERRUPTS THE THREAD!
 */
public class RuntimeInterruptedException extends RuntimeException {
    public RuntimeInterruptedException(InterruptedException ex) {
        super(ex);
        Thread.currentThread().interrupt();
    }
}

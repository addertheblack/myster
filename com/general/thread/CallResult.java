
package com.general.thread;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

class CallResult<T> {
    public static <T> CallResult<T> createResult(T value) {
        return new CallResult(value, null);
    }
    
    public static <T> CallResult<T> createException(Exception exception) {
        return new CallResult<T>(null, exception);
    }
    
    public static <T> CallResult<T> createCancelled() {
        return new CallResult<T>();
    }
    
    private final T value;
    private final Exception exception;
    private final boolean cancelled;
    
    private CallResult(T value, Exception exception) {
        this.value = value;
        this.exception = exception;
        cancelled = false;
    }

    private CallResult() {
        cancelled = true;
        value = null;
        exception = null;
    }
    
    public boolean isCancelled() {
        return cancelled;
    }
    
    public boolean isResult() {
        return !cancelled && exception == null;
    }
    
    public boolean isException() {
        return exception != null;
    }
    
    public T get() throws ExecutionException, CancellationException {
        if (cancelled) {
            throw new CancellationException();
        }
        
        if (exception != null) {
            throw new ExecutionException(exception);
        }
        
        return value;
    }
}

package com.general.thread;

import java.lang.StackWalker.StackFrame;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

public class CallResult<T> {
    public static <T> CallResult<T> createResult(T value) {
        return new CallResult<T>(value, null);
    }
    
    public static <T> CallResult<T> createException(Throwable exception) {
        return new CallResult<T>(null, exception);
    }
    
    public static <T> CallResult<T> createCancelled() {
        return new CallResult<T>();
    }
    
    private final T value;
    private final Throwable exception;
    private final boolean cancelled;
    
    private CallResult(T value, Throwable exception) {
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
    
    void enhanceExceptionWithContext(List<StackFrame> frames) {
        if (exception == null) {
            return;
        }
        
        StackTraceElement[] currentStackTrace = exception.getStackTrace();
        StackTraceElement[] additionalStackTrace = frames.stream()
                .map(frame -> frame.toStackTraceElement())
                .toArray(StackTraceElement[]::new);
        StackTraceElement divider = new StackTraceElement("!!!! PromiseFuture Async Context", "------------->>>", null, -1);
        
        StackTraceElement[] newStackTrace = new StackTraceElement[currentStackTrace.length + additionalStackTrace.length+1];
        System.arraycopy(currentStackTrace, 0, newStackTrace, 0, currentStackTrace.length);
        newStackTrace[currentStackTrace.length] = divider;
        System.arraycopy(additionalStackTrace, 0, newStackTrace, currentStackTrace.length+1, additionalStackTrace.length);
        exception.setStackTrace(newStackTrace);
        
    }
    
    public T get() throws ExecutionException, CancellationException {
        if (cancelled) {
            throw new CancellationException();
        }
        
        if (exception != null) {
            ExecutionException ex = new ExecutionException(exception);
//            ex.setStackTrace(new StackTraceElement[] {new StackTraceElement("---------- Useless stack trace omitted", "----------------", null, -1)});
            throw ex;
        }
        
        return value;
    }

    /**
     * does not wait until finished. Does not block.
     * 
     * @return result if any or null if no result or otherwise not called
     *         appropriately or if null value
     */
    public T getResult() {
        if (!isResult()) {
            throw new IllegalStateException("CallResult object was called with getResult() but does not represent a result.");
        }

        return value;
    }

    public Throwable getException() {
        if (!isException()) {
            throw new IllegalStateException("CallResult object was called with getException() but does not represent an exception.");
        }

        return exception;
    }
}
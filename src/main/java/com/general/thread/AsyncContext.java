
package com.general.thread;

public interface AsyncContext<R> extends Cancellable {
    /**
     * You probably want to call {@link AsyncContext#setResult(Object)} or
     * {@link AsyncContext#setException(Exception)} which call this with the right arguments.
     * 
     * @param result
     *            the result to set
     * @return true if the result was set false otherwise
     */
    boolean setCallResult(CallResult<R> result);
    
    default boolean setResult(R result) {
        return setCallResult(CallResult.createResult(result));
    }

    default boolean setException(Exception exception) {
        return setCallResult(CallResult.createException(exception));
    }
    
    @Override
    default void cancel() {
        setCallResult(CallResult.createCancelled());
    }
    
    boolean isCancelled();
    void registerDependentTask(Cancellable... c);
    
    /**
     * If you create sub tasks you need to be sure to register them with this async context
     * or else task cancellation won't work correctly
     */
    default void registerDependentTask(PromiseFuture<?>... p) {
        registerDependentTask((Cancellable[]) p);
    }
}

package com.general.thread;

public interface AsyncContext<R> extends Cancellable, TaskTracker {
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

    default boolean setException(Throwable exception) {
        return setCallResult(CallResult.createException(exception));
    }
    
    @Override
    default void cancel() {
        setCallResult(CallResult.createCancelled());
    }
    
    boolean isCancelled();
    @Override
    void trackForCancellation(Cancellable... tasks);
    
    /**
     * Tracks future-backed operations that this context owns so cancellation
     * reaches them. Their completion is not propagated to this context.
     *
     * @param futures futures to cancel with this context
     */
    default void trackForCancellation(PromiseFuture<?>... futures) {
        trackForCancellation((Cancellable[]) futures);
    }
}

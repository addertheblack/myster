
package com.general.thread;

import java.util.concurrent.Future;
import java.util.function.Consumer;

public interface PromiseFuture<T> extends Cancellable, Future<T> {
    public static <R> PromiseFuture<R> newPromiseFuture(Consumer<AsyncContext<R>> context) {
        PromiseFutureImpl<R> f = new PromiseFutureImpl<>();
        
        context.accept(f.getAsyncContext());
        
        return f;
    }
    
    public static <R> PromiseFuture<R> newPromiseFuture(R r) {
        PromiseFutureImpl<R> f = new PromiseFutureImpl<>();
        
        f.getAsyncContext().setResult(r);
        
        return f;
    }
    
    public static <R> PromiseFuture<R> newPromiseFutureException(Exception e) {
        PromiseFutureImpl<R> f = new PromiseFutureImpl<>();
        
        f.getAsyncContext().setException(e);
        
        return f;
    }
    
    PromiseFuture<T> clearInvoker();
    
    PromiseFuture<T> setInvoker(Invoker invoker);
    
    Invoker getInvoker();
    
    /**
     * Similar to {@link #addCallResultListener(Consumer)} but completely
     * synchronous. Does not use the invoker at all. Will always return the
     * result immediately on whatever thread caused the result to be set. Useful
     * for when you need a callback but don't give a crap about the invoker
     * thread. Also very dangerous because you have no idea what thread upstream
     * you're using or what locks are held.
     */
    void addSynchronousCallback(Consumer<CallResult<T>> c);

    default PromiseFuture<T> useEdt() {
        return setInvoker(Invoker.EDT);
    }
    
    PromiseFuture<T> addCallResultListener(Consumer<CallResult<T>> c);

    PromiseFuture<T> addCallListener(CallListener<T> callListener);

    PromiseFuture<T> addResultListener(Consumer<T> resultListener);

    PromiseFuture<T> addExceptionListener(Consumer<Throwable> exceptionListener);

    PromiseFuture<T> addFinallyListener(Runnable runnable);

    PromiseFuture<T> addCancelLisener(Runnable cancelLisener);

    PromiseFuture<T> addStandardExceptionHandler();
}


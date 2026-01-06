
package com.general.thread;

import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Function;

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
     * Similar to {@link #addFinallyCallResultListener(Consumer)} but completely
     * synchronous. Does not use the invoker at all. Will always return the
     * result immediately on whatever thread caused the result to be set. Useful
     * for when you need a callback but don't give a crap about the invoker
     * thread. Also very dangerous because you have no idea what thread upstream
     * you're using or what locks are held.
     */
    PromiseFuture<T> addSynchronousCallback(Consumer<CallResult<T>> c);

    default PromiseFuture<T> useEdt() {
        return setInvoker(Invoker.EDT);
    }
    
    /**
     * Is always called once a task finished. Similar to addFinally but with a callResult
     */
    PromiseFuture<T> addFinallyCallResultListener(Consumer<CallResult<T>> c);

    PromiseFuture<T> addCallListener(CallListener<T> callListener);

    PromiseFuture<T> addResultListener(Consumer<T> resultListener);

    PromiseFuture<T> addExceptionListener(Consumer<Throwable> exceptionListener);

    PromiseFuture<T> addFinallyListener(Runnable runnable);

    PromiseFuture<T> addCancelLisener(Runnable cancelLisener);

    PromiseFuture<T> addStandardExceptionHandler();
    
    /**
     * Maps the result of this PromiseFuture to another PromiseFuture
     * asynchronously. Does NOT map the invoker
     * 
     * @param <R>
     */
    default <R> PromiseFuture<R> mapAsync(Function<T, PromiseFuture<R>> mapAsync) {
        return PromiseFuture.newPromiseFuture(context -> {
            addSynchronousCallback(c -> {
                if (c.isException()) {
                    context.setException(c.getException());
                } else if (c.isCancelled()) {
                    context.cancel();
                } else {
                    context.registerDependentTask(mapAsync.apply(c.getResult())
                            .addSynchronousCallback(context::setCallResult));
                }
            });
        });
    }
}


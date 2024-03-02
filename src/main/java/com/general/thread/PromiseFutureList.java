
package com.general.thread;

import java.util.List;
import java.util.function.Consumer;

public interface PromiseFutureList<T> extends PromiseFuture<List<T>> {
    public static <T> PromiseFutureList<T> newPromiseFutureList(Consumer<AsyncContextList<T>> consumer) {
        PromiseFutureListImpl<T> futureListImpl = new PromiseFutureListImpl<T>();
        consumer.accept(futureListImpl.getAsyncListContext());
        
        return futureListImpl;
    }
    
    PromiseFutureList<T> addPartialResultListener(Consumer<T> listener);
    
    PromiseFutureList<T> clearInvoker();
    PromiseFutureList<T> setInvoker(Invoker invoker);
    PromiseFutureList<T> useEdt();
    PromiseFutureList<T> addCallResultListener(Consumer<CallResult<List<T>>> c);
    PromiseFutureList<T> addCallListener(CallListener<List<T>> callListener);
    PromiseFutureList<T> addResultListener(Consumer<List<T>> resultListener);
    PromiseFutureList<T> addExceptionListener(Consumer<Throwable> exceptionListener);
    PromiseFutureList<T> addFinallyListener(Runnable runnable);
    PromiseFutureList<T> addCancelLisener(Runnable cancelLisener);
    PromiseFutureList<T> addStandardExceptionHandler();
    
    /**
     * Dispatches 100% synchronously
     */
    PromiseFutureList<T> addSynchronousPartialCallback(Consumer<T> c);
}

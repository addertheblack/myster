
package com.general.thread;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;

public interface PromiseFuture<T> extends Cancellable, Future<T> {
    public static <R> PromiseFuture<R> newPromiseFuture(Consumer<AsyncContext<R>> context) {
        PromiseFutureImpl<R> f = new PromiseFutureImpl<>();
        
        context.accept(f.getAsyncContext());
        
        return f;
    }
    
    PromiseFuture<T> setInvoker(Invoker invoker);

    default PromiseFuture<T> useEdt() {
        return setInvoker(Invoker.EDT);
    }
    
    PromiseFuture<T> addCallResultListener(Consumer<CallResult<T>> c);

    default PromiseFuture<T> addCallListener(CallListener<T> callListener) {
        return addCallResultListener((c)-> {
            try {
                callListener.handleResult(c.get());
            } catch (CancellationException exception) {
                callListener.handleCancel();
            } catch (ExecutionException exception) {
                callListener.handleException(exception);
            } finally {
                callListener.handleFinally();
            }
        });
    }

    default PromiseFuture<T> addResultListener(Consumer<T> resultListener) {
        return addCallListener(new CallAdapter<>() {
            @Override
            public void handleResult(T result) {
                resultListener.accept(result);
            }
        });
    }

    default PromiseFuture<T> addExceptionListener(Consumer<Exception> exceptionListener) {
        return addCallListener(new CallAdapter<>() {
            @Override
            public void handleException(Exception exception) {
                exceptionListener.accept(exception);
            }
        });
    }

    default PromiseFuture<T> addFinallyListener(Runnable runnable) {
        return addCallListener(new CallAdapter<>() {
            @Override
            public void handleFinally() {
                runnable.run();
            }
        });
    }

    default PromiseFuture<T> addCancelLisener(Runnable cancelLisener) {
        return addCallListener(new CallAdapter<>() {
            @Override
            public void handleException(Exception exception) {
                cancelLisener.run();
            }
        });
    }

    default PromiseFuture<T> addStandardExceptionHandler() {
        return addCallListener(new CallAdapter<>() {
            @Override
            public void handleException(Exception exception) {
                exception.printStackTrace();
            }
        });
    }
}


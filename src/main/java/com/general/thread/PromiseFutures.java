
package com.general.thread;

import java.util.concurrent.Executor;

public final class PromiseFutures {
    public static <T> PromiseFuture<T> execute(CancellableCallable<T> callable, Executor executor) {
        return PromiseFuture.<T>newPromiseFuture((context)-> {
             context.registerDependentTask(callable);
             
             executor.execute(()-> {
                 try {
                     context.setResult(callable.call());
                 } catch (Exception exception) {
                     context.setException(exception);
                 }
             });
         });
     }
}

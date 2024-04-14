
package com.general.thread;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public final class PromiseFutures {
    public static <T> PromiseFuture<T> execute(Callable<T> callable) {
        return execute(callable, Executors.newVirtualThreadPerTaskExecutor());
     }

     public static <T> PromiseFuture<T> execute(Callable<T> callable, Executor executor) {
         return PromiseFuture.<T> newPromiseFuture((context) -> {
             if (callable instanceof Cancellable c) {
                 context.registerDependentTask(c);
             }

             executor.execute(() -> {
                 try {
                     context.setResult(callable.call());
                 } catch (Exception exception) {
                     context.setException(exception);
                 }
             });
         });
     }

     public static <T> PromiseFuture<List<T>> all(List<PromiseFuture<T>> futures) {
         return PromiseFuture.newPromiseFuture(context -> {
             List<T> result = Collections.synchronizedList(new ArrayList<>((Collections
                     .nCopies(futures.size(), null))));

             for (int i = 0; i < futures.size(); i++) {
                 PromiseFuture<T> f = futures.get(i);

                 final int index = i;
                 f.addSynchronousCallback(r -> {
                     if (r.isException()) {
                         context.setException(r.getException());
                     } else if (r.isCancelled()) {
                         context.setException(new CancellationException());
                     } else {
                         result.set(index, r.getResult());
                     }
                 });
             }

             context.setResult(result);
         });
     }
 }

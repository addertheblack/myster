
package com.general.thread;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

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

     /**
      * Waits for all futures to complete. If any future fails or is cancelled,
      * the returned future will also fail or be cancelled.
      * 
      * @param futures the list of futures to wait for
      * @return a future that completes when all futures complete
      */
     public static <T> PromiseFuture<List<T>> all(List<PromiseFuture<T>> futures) {
         return PromiseFuture.newPromiseFuture(context -> {
             if (futures.isEmpty()) {
                 context.setResult(Collections.emptyList());
                 return;
             }
             
             List<T> result = Collections.synchronizedList(new ArrayList<>((Collections
                     .nCopies(futures.size(), null))));
             
             // Thread-safe counter to track completed callbacks
             final AtomicInteger completedCount = new AtomicInteger(0);
             final int expectedCount = futures.size();

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
                         
                         // Check if all callbacks have completed
                         if (completedCount.incrementAndGet() == expectedCount) {
                             context.setResult(result);
                         }
                     }
                 });
             }
         });
     }

     /**
      * Waits for all futures to complete and returns their CallResults.
      *
      * @param futures the list of futures to wait for
      * @return a future that completes with a list of CallResults when all futures complete
      */
     public static <T> PromiseFuture<List<CallResult<T>>> allCallResults(List<PromiseFuture<T>> futures) {
         return PromiseFuture.newPromiseFuture(context -> {
             if (futures.isEmpty()) {
                 context.setResult(Collections.emptyList());
                 return;
             }
             
             List<CallResult<T>> result = Collections.synchronizedList(new ArrayList<>((Collections
                     .nCopies(futures.size(), null))));
             
             // Thread-safe counter to track completed callbacks
             final AtomicInteger completedCount = new AtomicInteger(0);
             final int expectedCount = futures.size();

             for (int i = 0; i < futures.size(); i++) {
                 PromiseFuture<T> f = futures.get(i);

                 final int index = i;
                 f.addSynchronousCallback(r -> {
                     if (r.isCancelled()) {
                         context.setException(new CancellationException());
                     } else {
                         result.set(index, r);
                         
                         // Check if all callbacks have completed
                         if (completedCount.incrementAndGet() == expectedCount) {
                             context.setResult(result);
                         }
                     }
                 });
             }
         });
     }
 }

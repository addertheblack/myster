
package com.general.thread;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

public class PromiseFutureImpl<T> implements PromiseFuture<T>, AsyncContext<T> {
    private Invoker invoker;
    private CallResult<T> result = null;
    private final CountDownLatch latch = new CountDownLatch(1);
    private final List<Cancellable> cancellables = new ArrayList<>();
    private final List<Consumer<CallResult<T>>> listeners = new ArrayList<>();

    // implement PromiseFuture
    public synchronized PromiseFutureImpl<T> setInvoker(Invoker invoker) {
        if (this.invoker != null ) {
            throw new IllegalStateException("Invoker already set");
        }
        
        this.invoker = invoker;
        
        checkForDispatch();
        
        return this;
    }
    
    public PromiseFutureImpl<T> useEdt() {
        return setInvoker(Invoker.EDT);
    }
    
    @Override
    public synchronized boolean setCallResult(CallResult<T> result) {
        if(result.isCancelled()) {
            cancel();
            
            return true;
        }
        
        if (isDone() || isCancelled()) {
            return false;
        }
        
        this.result = result;
        
        checkForDispatch();
        
        latch.countDown();
        
        return true;
    }
    
    @Override
    public synchronized void cancel() {
        this.result =  CallResult.createCancelled();
        
        checkForDispatch();
        
        latch.countDown();
        
        for (Cancellable cancellable : cancellables) {
            cancellable.cancel();
        }
    }

    private void checkForDispatch() {
        if (invoker == null) {
            return;
        }
        
        if (this.result == null) {
            return;
        }
        
        if (listeners.size() == 0) {
            return;
        }
        
        invoker.invoke(()-> {
            List<Consumer<CallResult<T>>> toDispatch = null;
            CallResult<T> resultToDispatch = null;
            
            synchronized (PromiseFutureImpl.this) {
                toDispatch = new ArrayList<>(listeners);
                listeners.clear();
                 resultToDispatch = result;
            }
            
            // it's important that these are get done in one shot so that finalizers execute
            // without potential for pre-emption
            for (Consumer<CallResult<T>> consumer : toDispatch) {
                try {
                    consumer.accept(resultToDispatch);
                }catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        });
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        if (isDone()) {
            return false;
        }
        
        cancel();
        return true;
    }

    @Override
    public boolean isCancelled() {
        return result == null ? false : result.isCancelled();
    }

    @Override
    public boolean isDone() {
        return result != null;
    }

    @Override
    public synchronized PromiseFuture<T> addCallResultListener(Consumer<CallResult<T>> c) {
        listeners.add(c);
        
        checkForDispatch();
        
        return this;
    }

    @Override
    public synchronized void registerDependentTask(Cancellable... c) {
        if (isCancelled()) {
            for (Cancellable cancellable : c) {
                cancellable.cancel();
            }
        } else {
            cancellables.addAll(Arrays.asList(c));
        }
    }

    @Override
    public T get() throws InterruptedException, ExecutionException, CancellationException {
        if (invoker.isInvokerThread()) {
            throw new IllegalStateException("get() Called on invoker thread");
        }
        
        latch.await();
        
        return result.get();
    }

    @Override
    public T get(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        if (invoker.isInvokerThread()) {
            throw new IllegalStateException("get() Called on invoker thread");
        }
        
        latch.await(timeout, unit);
        
        return result.get();
    }
}

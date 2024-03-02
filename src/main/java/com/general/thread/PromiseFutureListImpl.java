
package com.general.thread;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class PromiseFutureListImpl<T> extends PromiseFutureImpl<List<T>> implements PromiseFutureList<T> {
    private final List<Consumer<T>> elementListeners = new ArrayList<>();
    private final List<Consumer<T>> synchronousPartialCallbacks = new ArrayList<>();
    
    private final List<T> elements = new ArrayList<>();
        
    @Override
    public synchronized PromiseFutureList<T> addPartialResultListener(Consumer<T> listener) {
        if (isCancelled()) {
            return this;
        }
        
        if (!isDone()) {
            elementListeners.add(listener);
        }
        
        dispatchAllPartialResultsSoFar(listener);
        
        return this;
    }

    private void dispatchAllPartialResultsSoFar(Consumer<T> listener) {
        if (isCancelled()) {
            return;
        }
        
        List<T> elementsCopy = new ArrayList<>(elements);
        getInvoker().invoke(() -> {
            elementsCopy.forEach(listener);
        });
    }
    
    @Override
    public PromiseFutureList<T> addSynchronousPartialCallback(Consumer<T> c) {
        List<T> elementsCopy = new ArrayList<>();
        synchronized (this) {
            if (isCancelled()) {
                return this;
            }
            
            if (!isDone()) {
                synchronousPartialCallbacks.add(c);
            }
            elementsCopy.addAll(elements);
        }
        
        elementsCopy.forEach(c);
        
        return this;
    }
    
    AsyncContextList<T> getAsyncListContext() {
        return new AsyncContextListImpl<T>(super.getAsyncContext());
    }

    private synchronized boolean addResultPrivate(T t) {
        if (isDone()) {
            return false;
        }
        elements.add(t);

        List<Consumer<T>> elementListenersCopy = new ArrayList<>(elementListeners);
        getInvoker().invoke(() -> {
            elementListenersCopy.forEach(l -> dispatchElement(l, t));
        });
        
        return true;
    }
    
    private void dispatchElement(Consumer<T> c, T t) {
        try {
            c.accept(t);
        } catch (Exception ex ) { 
            ex.printStackTrace();
        }
    }
    
    private class AsyncContextListImpl<Z extends T> implements AsyncContextList<Z> {
        private AsyncContext<List<Z>> asyncContext;

        public AsyncContextListImpl(AsyncContext<List<Z>> asyncContext) {
            this.asyncContext = asyncContext;
        }

        @Override
        public void cancel() {
            asyncContext.setCallResult(CallResult.createCancelled());
        }

        @Override
        public boolean isCancelled() {
            return asyncContext.isCancelled();
        }

        @Override
        public void registerDependentTask(Cancellable... c) {
            asyncContext.registerDependentTask(c);
        }

        @Override
        public boolean addResult(Z t) {
            return PromiseFutureListImpl.this.addResultPrivate(t);
        }

        @SuppressWarnings("unchecked")
        @Override
        public void done() {
            asyncContext.setResult((List<Z>)elements);
        }
    }
    

    // implement PromiseFuture
    public synchronized PromiseFutureList<T> setInvoker(Invoker invoker) {
        super.setInvoker(invoker);
        
        return this;
    }
    
    public PromiseFutureList<T> useEdt() {
        setInvoker(Invoker.EDT);
        
        return this;
    }
    

    @Override
    public synchronized PromiseFutureList<T> addCallResultListener(Consumer<CallResult<List<T>>> consumer) {
        super.addCallResultListener(consumer);
        
        return this;
    }


    @Override
    public PromiseFutureList<T> clearInvoker() {
        return PromiseFutureList.newPromiseFutureList(context -> {
            context.registerDependentTask(this);
            this.addSynchronousPartialCallback(context::addResult);  
            this.addSynchronousCallback(e -> context.done());
        });
    }

    @Override
    public PromiseFutureList<T> addCallListener(CallListener<List<T>> callListener) {
        super.addCallListener(callListener);
        
        return this;
    }

    @Override
    public PromiseFutureList<T> addResultListener(Consumer<List<T>> resultListener) {
        super.addResultListener(resultListener);
        
        return this;
    }

    @Override
    public PromiseFutureList<T> addExceptionListener(Consumer<Throwable> exceptionListener) {
        super.addExceptionListener(exceptionListener);
        
        return this;
    }

    @Override
    public PromiseFutureList<T> addFinallyListener(Runnable runnable) {
        super.addFinallyListener(runnable);
        
        return this;
    }

    @Override
    public PromiseFutureList<T> addCancelLisener(Runnable cancelLisener) {
        super.addCancelLisener(cancelLisener);
        
        return this;
    }

    @Override
    public PromiseFutureList<T> addStandardExceptionHandler() {
        super.addStandardExceptionHandler();
        
        return this;
    }
}

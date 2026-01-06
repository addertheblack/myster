
package com.general.thread;

import java.lang.StackWalker.StackFrame;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class PromiseFutureImpl<T> implements PromiseFuture<T> {
    private final InvokerContainer invoker;
    private final CountDownLatch latch = new CountDownLatch(1);
    private final List<Cancellable> cancellables = new ArrayList<>();
    private final List<Consumer<CallResult<T>>> listeners = new ArrayList<>();
    
    private CallResult<T> result = null;
    private List<Consumer<CallResult<T>>> synchronousCallbacks = new ArrayList<>();
    
    private final List<StackFrame> stackElements;
    
    private static final StackWalker stackWalker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);


    PromiseFutureImpl() {
        invoker = new InvokerContainerImpl();
        stackElements = stackWalker.walk(stream -> stream
                                         .limit(10)
                                         .collect(Collectors.toList()));
    }
    
    PromiseFutureImpl(PromiseFutureImpl<?> parent) {
        invoker = parent.invoker;
        
        stackElements = stackWalker.walk(stream -> stream
                         .limit(10)
                         .collect(Collectors.toList()));
    }

    // implement PromiseFuture
    public synchronized PromiseFuture<T> setInvoker(Invoker invoker) {
        if (this.invoker.getInvoker() != null ) {
            throw new IllegalStateException("Invoker already set");
        }
        
        this.invoker.setInvoker(invoker);
        
        checkForDispatch();
        
        return this;
    }
    
    public PromiseFuture<T> useEdt() {
        return setInvoker(Invoker.EDT);
    }
    
    AsyncContext<T> getAsyncContext() {
    	return new AsyncContextImpl();
    }
    
    class AsyncContextImpl implements AsyncContext<T> {
		@Override
		public boolean setCallResult(CallResult<T> r) {
            synchronized (PromiseFutureImpl.this) {
                if (isDone() || isCancelled()) {
                    return false;
                }

                result = r;
                result.enhanceExceptionWithContext(stackElements);

                checkForDispatch();

                latch.countDown();

                return true;
            }
		}
		

        @Override
        public void registerDependentTask(Cancellable... c) {
            synchronized (PromiseFutureImpl.this) {
                if (isCancelled()) {
                    for (Cancellable cancellable : c) {
                        cancellable.cancel();
                    }
                } else {
                    cancellables.addAll(Arrays.asList(c));
                }
            }
        }


        @Override
        public boolean isCancelled() {
            return PromiseFutureImpl.this.isCancelled();
        }
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
    
    private void checkForSyncDispatch() {
        if (this.result == null) {
            return;
        }
        
        if (this.synchronousCallbacks.size() == 0) {
            return;
        }
        
        List<Consumer<CallResult<T>>> toDispatch = null;
        CallResult<T> resultToDispatch = null;
        synchronized (PromiseFutureImpl.this) {
            toDispatch = new ArrayList<>(synchronousCallbacks);
            synchronousCallbacks.clear();
            resultToDispatch = result;
        }
        
        dispatchList(toDispatch, resultToDispatch);
    }

    private void checkForDispatch() {
        checkForSyncDispatch();
        
        if (this.result == null) {
            return;
        }
        
        if (invoker.getInvoker() == null) {
            return;
        }
        
        // Just so we don't bother the invoker for nothing
        synchronized (this) {
            if (listeners.size() == 0) {
                return;
            }
        }
        
        
        invoker.getInvoker().invoke(()-> {
            List<Consumer<CallResult<T>>> toDispatch = null;
            CallResult<T> resultToDispatch = null;
            
            synchronized (PromiseFutureImpl.this) {
                toDispatch = new ArrayList<>(listeners);
                listeners.clear();
                 resultToDispatch = result;
            }
            
            dispatchList(toDispatch, resultToDispatch);
        });
    }

    private void dispatchList(List<Consumer<CallResult<T>>> toDispatch,
                              CallResult<T> resultToDispatch) {
        // it's important that these are get done in one shot so that finalizers execute
        // without potential for pre-emption
        for (Consumer<CallResult<T>> consumer : toDispatch) {
            try {
                consumer.accept(resultToDispatch);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    @Override
    public synchronized boolean cancel(boolean mayInterruptIfRunning) {
        if (isDone()) {
            return false;
        }
        
        cancel();
        return true;
    }

    @Override
    public synchronized boolean isCancelled() {
        return result == null ? false : result.isCancelled();
    }

    @Override
    public synchronized boolean isDone() {
        return result != null;
    }

    @Override
    public synchronized PromiseFuture<T> addFinallyCallResultListener(Consumer<CallResult<T>> c) {
        listeners.add(c);
        
        checkForDispatch();
        
        return this;
    }

    @Override
    public T get() throws InterruptedException, ExecutionException, CancellationException {
        if (invoker.getInvoker() != null && invoker.getInvoker().isInvokerThread()) {
			throw new IllegalStateException("get() Called on invoker thread");
		}

		latch.await();

		synchronized (this) {
			return result.get();
		}
	}

	@Override
	public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
		if (invoker.getInvoker() != null && invoker.getInvoker().isInvokerThread()) {
			throw new IllegalStateException("get() Called on invoker thread");
		}

		latch.await(timeout, unit);

		synchronized (this) {
			return result.get();
		}
	}

    @Override
    public Invoker getInvoker() {
        return invoker.getInvoker();
    }

    @Override
    public PromiseFuture<T> addSynchronousCallback(Consumer<CallResult<T>> c) {
        synchronized(this) {
            synchronousCallbacks.add(c);
        }
        
        checkForSyncDispatch();
        
        return this;
    }
    
    @Override
    public PromiseFuture<T> clearInvoker() {
        return PromiseFuture.newPromiseFuture(c -> {
            c.registerDependentTask(this);
            this.addSynchronousCallback(c::setCallResult);
        });
    }

    @Override
    public PromiseFuture<T> addCallListener(CallListener<T> callListener) {
        return addFinallyCallResultListener((c)-> {
            try {
                callListener.handleResult(c.get());
            } catch (CancellationException exception) {
                callListener.handleCancel();
            } catch (ExecutionException exception) {
                callListener.handleException(exception.getCause());
            } finally {
                callListener.handleFinally();
            }
        });
    }

    @Override
    public PromiseFuture<T> addResultListener(Consumer<T> resultListener) {
        return addCallListener(new CallAdapter<>() {
            @Override
            public void handleResult(T result) {
                resultListener.accept(result);
            }
        });
    }

    @Override
    public PromiseFuture<T> addExceptionListener(Consumer<Throwable> exceptionListener) {
        return addCallListener(new CallAdapter<>() {
            @Override
            public void handleException(Throwable exception) {
                exceptionListener.accept(exception);
            }
        });
    }

    @Override
    public PromiseFuture<T> addFinallyListener(Runnable runnable) {
        return addCallListener(new CallAdapter<>() {
            @Override
            public void handleFinally() {
                runnable.run();
            }
        });
    }

    @Override
    public PromiseFuture<T> addCancelLisener(Runnable cancelLisener) {
        return addCallListener(new CallAdapter<>() {
            @Override
            public void handleException(Throwable exception) {
                cancelLisener.run();
            }
        });
    }

    @Override
    public PromiseFuture<T> addStandardExceptionHandler() {
        return addCallListener(new CallAdapter<>() {
            @Override
            public void handleException(Throwable exception) {
                exception.printStackTrace();
            }
        });
    }
}

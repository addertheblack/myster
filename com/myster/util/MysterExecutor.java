/*
 * Created on Oct 9, 2004
 * by Andrew Trumper
 */
package com.myster.util;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.general.thread.CallListener;
import com.general.thread.CancellableCallable;
import com.general.thread.Executor;
import com.general.thread.Future;
import com.general.util.BlockingQueue;
import com.general.util.Util;

/**
 * @author Andrew Trumper
 */
public class MysterExecutor implements Executor {
    private static MysterExecutor executor;

    public synchronized static MysterExecutor getInstance() {
        if (executor == null) {
            executor = new MysterExecutor();
            executor.start();
        }
        return executor;
    }

    private Thread[] threads;

    private BlockingQueue queue;

    public MysterExecutor() {
        queue = new BlockingQueue();
    }

    public void start() {
        threads = new Thread[3];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new PooledThread(queue);
            threads[i].start();
        }
    }

    public Future execute(CancellableCallable callable, CallListener listener) {
        Future future = new ExecutableTask(callable, listener);

        queue.add(future);

        return future;
    }

    private static class ExecutableTask<T> implements Future<T> {
        private final CancellableCallable callable;

        private final CallListener listener;

        private boolean cancelled = false;

        private volatile boolean done = false;
        private volatile T result;
        private volatile Exception exception;

        public ExecutableTask(CancellableCallable callable, CallListener listener) {
            this.callable = callable;
            this.listener = listener;
        }

        @Override
        public T get() throws InterruptedException, ExecutionException {
            while(!done) {
                synchronized (this) {
                    this.wait();
                }
            }
            
            if (cancelled) {
                throw new CancellationException();
            }
            
            if (exception != null) {
               throw new ExecutionException(exception);
            }
            
            return result;
        }
        
        public synchronized boolean cancel() {
            cancel(true);
            
            return false;
        }

        public synchronized boolean cancel(boolean mayInterruptIfRunning) {
            if (isCancelled())
                return false;
            if (isDone())
                return false;

            callable.cancel();

            cancelled = true;
            done = true;
            
            return false;
        }

        public synchronized boolean isCancelled() {
            return cancelled;
        }

        public synchronized boolean isDone() {
            return done;
        }

        public CancellableCallable getCallable() {
            return callable;
        }

        public CallListener<T> getListener() {
            return listener;
        }

        public synchronized void signalDone(T result, Exception ex) {
            if (done) {
                throw new IllegalStateException("signalDone() called twice");
            }
            
            this.result = result;
            this.exception = ex;
            this.done = true;
            
            notifyAll();
        }

        @Override
        public T get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            throw new IllegalStateException("Not implemented yet");
        }
    }

    private static class PooledThread extends Thread {
        private BlockingQueue queue;

        public PooledThread(BlockingQueue queue) {
            this.queue = queue;
        }

        public void run() {
            for (;;) {
                try {
                    final ExecutableTask executableTask = (ExecutableTask) queue.get();

                    try {
                        final Object result = executableTask.getCallable().call();
                        Util.invokeLater(new Runnable() {
                            public void run() {
                                if (executableTask.isCancelled()) {
                                    handleCancel(executableTask.getListener());
                                    return;
                                }
                                executableTask.signalDone(result, null);
                                executableTask.getListener().handleResult(result);
                                executableTask.getListener().handleFinally();
                            }
                        });
                    } catch (final Exception ex) {
                        Util.invokeLater(new Runnable() {
                            public void run() {
                                if (executableTask.isCancelled()) {
                                    handleCancel(executableTask.getListener());
                                    return;
                                }
                                
                                executableTask.signalDone(null, ex);
                                executableTask.getListener().handleException(ex);
                                executableTask.getListener().handleFinally();
                            }
                        });
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }

        public static <T> void handleCancel(final CallListener<T> listener) {
            listener.handleCancel();
            listener.handleFinally();
        }
    }
}
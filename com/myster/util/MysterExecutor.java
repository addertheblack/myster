/*
 * Created on Oct 9, 2004
 * by Andrew Trumper
 */
package com.myster.util;

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

    private static class ExecutableTask implements Future {
        private final CancellableCallable callable;

        private final CallListener listener;

        private boolean cancelled = false;

        private boolean done = false;

        public ExecutableTask(CancellableCallable callable, CallListener listener) {
            this.callable = callable;
            this.listener = listener;
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

        public CallListener getListener() {
            return listener;
        }

        public synchronized void signalDone() {
            done = true;
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
                        executableTask.signalDone();
                        if (executableTask.isCancelled())
                            continue;
                        Util.invokeAndWait(new Runnable() {
                            public void run() {
                                executableTask.getListener().handleResult(result);
                            }
                        });
                    } catch (final Exception ex) {
                        executableTask.signalDone();
                        if (executableTask.isCancelled())
                            continue;
                        Util.invokeAndWait(new Runnable() {
                            public void run() {
                                executableTask.getListener().handleException(ex);
                            }
                        });
                    } finally {
                        if (executableTask.isCancelled())
                            handelCancel(executableTask.getListener());
                        Util.invokeAndWait(new Runnable() {
                            public void run() {
                                executableTask.getListener().handleFinally();
                            }
                        });
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
        
        public void handelCancel(final CallListener listener) throws InterruptedException {
            Util.invokeAndWait(new Runnable() {
                public void run() {
                    listener.handleFinally();
                }
            });
        }
    }
}
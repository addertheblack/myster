package com.myster.net;

import com.general.thread.CallListener;
import com.general.thread.CancellableCallable;
import com.general.thread.Executor;
import com.general.thread.Future;
import com.general.util.BlockingQueue;
import com.general.util.SafeThread;
import com.general.util.Util;

/**
 * This class should be used whenever the user wishes to do an asynchronous IO
 * operation using a socket but does not wish to overload the number of
 * available sockets.
 * 
 * NOTE: The user of this pool should remember not to spam it. This class is not
 * responsible for prioritize requests. Well behaved clients should throttle
 * their own number of requests so as to not monopolize this resource.
 * 
 * @see com.general.thread.Executor
 */

/*
 * Locking order is InternalFuture -> MysterSocketPool -> BlockingQueue
 */
public class MysterSocketPool implements Executor {
    private static MysterSocketPool pool;

    public static synchronized Executor getInstance() {
        if (pool == null) {
            pool = new MysterSocketPool(10);
            pool.start();
        }

        return pool;
    }

    ///////////////////////////////Object\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\
    private final BlockingQueue queuedTasks;

    private final PoolableThread[] threads;

    /**
     * 
     * @param numberOfThreads
     *            number of pooled threads to make. We recommend a sane value
     *            here.
     */
    public MysterSocketPool(final int numberOfThreads) {
        threads = new PoolableThread[numberOfThreads];
        queuedTasks = new BlockingQueue();
    }

    /**
     * Starts all the pooled threads.
     *  
     */
    public void start() {
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new PoolableThread(queuedTasks);
            threads[i].start();
        }
    }

    /**
     * Executes the task in fullfillment of the CancellablebleCallable /
     * Executor / Future / CallListener contract.
     */
    public synchronized Future execute(CancellableCallable callable, CallListener listener) {
        final InternalFuture future = new InternalFuture(callable, listener);

        queuedTasks.add(future);

        return future;
    }

    private boolean removeFromQueue(InternalFuture future) {
        return queuedTasks.remove(future);
    }

    /*
     * This class wraps the callable object and keeps track of the state of the
     * operation. It also gets information from parent's wrapped
     * InternalCallListener about when the task is "done" which it can then
     * allow listeners to do.
     */
    private class InternalFuture implements Future {
        private final CancellableCallable callable;

        private final CallListener listener;

        private boolean isDone = false;

        private boolean isCancelled = false;

        private InternalFuture(final CancellableCallable callable, final CallListener listener) {
            this.callable = callable;
            this.listener = listener;
        }

        public synchronized boolean cancel() {
            if (isCancelled)
                return false;
            if (isDone) //you can't cancel it if it's done!
                return false;

            isCancelled = true;
            callable.cancel();
            isDone = true;
            if (removeFromQueue(this)) {
                Util.invokeLater(new Runnable() {
                    public void run() {
                        listener.handleCancel();
                    }
                });
                Util.invokeLater(new Runnable() {
                    public void run() {
                        listener.handleFinally();
                    }
                });
            }
            return true;
        }

        public synchronized boolean cancel(boolean mayInterruptIfRunning) {
            return cancel();
        }

        public synchronized boolean isCancelled() {
            return isCancelled;
        }

        public synchronized boolean isDone() {
            return isDone;
        }

        private synchronized void setDone() {
            isDone = true;
        }

        public CancellableCallable getCallable() {
            return callable;
        }

        public CallListener getListener() {
            return listener;
        }
    }

    private static class PoolableThread extends SafeThread {
        private BlockingQueue queue;

        public PoolableThread(BlockingQueue queue) {
            this.queue = queue;
        }

        public void run() {
            while (true) {
                final CallListener listener;
                final CancellableCallable callable;
                final InternalFuture future;

                try {
                    future = (InternalFuture) queue.get();
                    if (future == null) continue; //because the queue could have items removed.
                    listener = future.getListener();
                    callable = future.getCallable();
                } catch (InterruptedException e) {
                    continue;
                }

                /*
                 * Remember the guarantee is that either handleCancel(),
                 * handleResult() or handleException is called THEN
                 * handleFinally(). This means we should only check ONCE to see
                 * if it's cancelled. Given that the task is being cancelled
                 * asynchronously means we have to commit to reading the "is
                 * cancelled" once and committing this operation to one thing or
                 * the other.
                 * 
                 * @see com.general.thread.Executor
                 */
                try {
                    if (future.isCancelled()) { //speed hack...
                        future.setDone();
                        cancelNow(listener);
                        continue;
                    }

                    final Object result = callable.call();

                    if (doCancelled(future)) {
                        continue;
                    }

                    Util.invokeLater(new Runnable() {
                        public void run() {
                            listener.handleResult(result);
                        }
                    });
                } catch (final InterruptedException ex) {
                    //interrupted exception
                    //We skip this 'cause we don't want to bother clients with
                    //stupid interrupted exceptions!
                } catch (final Exception ex) {
                    if (doCancelled(future)) {
                        continue;
                    }

                    Util.invokeLater(new Runnable() {
                        public void run() {
                            listener.handleException(ex);
                        }
                    });
                } catch (final Error ex) {
                    ex.printStackTrace();
                } finally {
                    Util.invokeLater(new Runnable() {
                        public void run() {
                            listener.handleFinally();
                        }
                    });
                }
            }
        }

        /*
         * Sets the future to done and does the right thing if it was cancelled.
         * 
         * @see com.general.thread.Executor
         */
        private boolean doCancelled(final InternalFuture future) {
            future.setDone();
            if (future.isCancelled()) {
                cancelNow(future.getListener());
                return true;
            }
            return false;
        }
        
        /*
         * Calls handleCancel() on the event thread... and that's all!
         */
        private void cancelNow(final CallListener listener) {
            Util.invokeLater(new Runnable() {
                public void run() {
                    listener.handleCancel();
                }
            });
        }
    }
}
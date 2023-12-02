package com.myster.net;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import com.general.thread.CallListener;
import com.general.thread.CancellableCallable;
import com.general.thread.PromiseFuture;
import com.general.thread.PromiseFutures;
import com.general.thread.SpecialExecutor;

/**
 * This class should be used whenever the user wishes to do an asynchronous IO
 * operation using a socket but does not wish to overload the number of
 * available sockets.
 * 
 * NOTE: The user of this pool should remember not to spam it. This class is not
 * responsible for prioritise requests. Well behaved clients should throttle
 * their own number of requests so as to not monopolize this resource.
 * 
 * @see com.general.thread.SpecialExecutor
 */

/*
 * Locking order is InternalFuture -> MysterSocketPool -> BlockingQueue
 */
public class MysterClientSocketPool implements SpecialExecutor {
    private static MysterClientSocketPool pool;

    public static synchronized SpecialExecutor getInstance() {
        if (pool == null) {
            pool = new MysterClientSocketPool(10);
        }

        return pool;
    }

    ///////////////////////////////Object\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\
    private final Executor threadPool;

    /**
     * 
     * @param numberOfThreads
     *            number of pooled threads to make. We recommend a sane value
     *            here.
     */
    public MysterClientSocketPool(final int numberOfThreads) {
        threadPool = Executors.newFixedThreadPool(numberOfThreads);
    }

    @Override
    public <T> PromiseFuture<T> execute(CancellableCallable<T> callable, CallListener<T> listener) {
        return PromiseFutures.execute(callable, threadPool).useEdt().addCallListener(listener);
    }
}
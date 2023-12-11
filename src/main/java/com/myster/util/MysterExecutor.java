/*
 * Created on Oct 9, 2004
 * by Andrew Trumper
 */
package com.myster.util;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.general.thread.CallListener;
import com.general.thread.CancellableCallable;
import com.general.thread.SpecialExecutor;
import com.general.thread.PromiseFuture;
import com.general.thread.PromiseFutures;
import com.general.util.BlockingQueue;
import com.general.util.Util;

/**
 * @author Andrew Trumper
 */
public class MysterExecutor implements SpecialExecutor {
    private static MysterExecutor mysterExecutor;

    public synchronized static MysterExecutor getInstance() {
        if (mysterExecutor == null) {
            mysterExecutor = new MysterExecutor();
        }
        return mysterExecutor;
    }

    private final ExecutorService executor;

    public MysterExecutor() {
          executor = Executors.newFixedThreadPool(4);
    }

    public <T> Future<T> execute(CancellableCallable<T> callable, CallListener<T> listener) {
       return PromiseFutures.<T>execute(callable, executor).addCallListener(listener).useEdt();
    }
}
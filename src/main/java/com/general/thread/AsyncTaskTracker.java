
package com.general.thread;

public class AsyncTaskTracker implements Cancellable {
    public static AsyncTaskTracker create(TaskTracker context, Invoker invoker) {
        AsyncTaskTracker task =  new AsyncTaskTracker(invoker);
        
        context.registerDependentTask(task);
        
        return task;
    }

    private static void checkThread(Invoker invoker) {
        if (!invoker.isInvokerThread()) {
            throw new IllegalStateException("doAsync() called off the invoker thread: "
                    + Thread.currentThread().getName());
        }
    }
    
    private final Invoker invoker;
    private final SimpleTaskTracker tasks = new SimpleTaskTracker();
    
    private int taskCount = 0;
    private boolean done = false;
    private boolean cancelled;
    private Runnable doneListener;
    
    private AsyncTaskTracker(Invoker invoker) {
        this.invoker = invoker;
    }
    
    public <T> PromiseFuture<T> doAsync(AsyncCallable<T> c) {
        checkThread(invoker);
        
        if (done) {
            throw new IllegalStateException("Task is done");
        }
        
        taskCount++;
        
        PromiseFuture<T> future = c.call().setInvoker(invoker).addFinallyListener(this::taskFinished);
        
        // if this object is cancelled this will be auto- cancelled by the SimpleTaskTracker
        tasks.registerDependentTask(future);
        
        return future;
    }

    private void taskFinished() {
        taskCount--;

        if (taskCount == 0) {
            invoker.invoke(() -> {
                if (taskCount == 0) {
                    done();
                }
            });
        }
    }

    private void done() {
        done = true;

        if (doneListener != null) {
            doneListener.run();
        }
    }

    public boolean isDone() {
        checkThread(invoker);
        
        return done;
    }
    
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void cancel() {
        checkThread(invoker);
        
        done = true;
        cancelled = true;
        tasks.cancel();
    }
    
    public AsyncTaskTracker setDoneListener(Runnable r) {
        if (taskCount > 0 || done) {
            throw new IllegalStateException("can't add listener, task is already in progress");
        }
        
        if ( doneListener != null) {
            throw new IllegalStateException("Done listener already exists");
        }
        
        doneListener = r;
        
        return this;
    }
}

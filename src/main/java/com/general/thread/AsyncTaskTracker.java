package com.general.thread;

/**
 * Manages a long task/promise that is composed of dynamically discovered child
 * promises.
 * <p>
 * Calls to {@link #doAsync(AsyncCallable)} and natural-completion callbacks are
 * confined to the configured invoker. {@link #cancel()} may be called from any
 * thread: cancellation becomes visible immediately and child cancellation is
 * performed on the invoker. A cancellation is not natural exhaustion and never
 * invokes the done listener.
 *
 * <p>The done listener runs when the tracked task count returns to zero after
 * at least one task was started. A caller with no initial work must complete
 * its enclosing operation directly.
 *
 * Example:
 * <code>
 *
 *                 // The invoker does not need to be EDT. It can be a specific threaded create just for this object
 *                 AsyncTaskTracker taskTracker =
 *                         AsyncTaskTracker.create(context, INVOKER);
 *                 taskTracker.setDoneListener(() -> context.setResult(null));
 *
 *                 // then start the tasks off and keep adding any new tasks that you need
 *                 AsyncNetworkCrawler
 *                         .startWork(log, protocol, searchIp, type, ipQueue,
 *                                    tracker::addIp, taskTracker);
 * </code>
 */
public class AsyncTaskTracker implements Cancellable {
    public static AsyncTaskTracker create(TaskTracker context, Invoker invoker) {
        AsyncTaskTracker taskTracker = new AsyncTaskTracker(invoker);

        context.trackForCancellation(taskTracker);

        return taskTracker;
    }

    private static void checkThread(Invoker invoker) {
        if (!invoker.isInvokerThread()) {
            throw new IllegalStateException("AsyncTaskTracker operation called off the invoker thread: "
                    + Thread.currentThread().getName());
        }
    }

    private final Invoker invoker;
    private final SimpleTaskTracker tasks = new SimpleTaskTracker();

    private int taskCount = 0;
    private volatile boolean done;
    private volatile boolean cancelled;
    private Runnable doneListener;

    private AsyncTaskTracker(Invoker invoker) {
        this.invoker = invoker;
    }

    /**
     * Starts and tracks one child operation. The callable and all ordinary
     * completion listeners run through this tracker's invoker.
     *
     * @param c operation that returns the child promise
     * @return child promise adapted to this tracker's invoker
     * @throws IllegalStateException if called off the invoker or after the
     *         tracker is done
     */
    public <T> PromiseFuture<T> doAsync(AsyncCallable<T> c) {
        checkThread(invoker);

        if (done) {
            throw new IllegalStateException("Task is done");
        }

        PromiseFuture<T> future = c.call().withInvoker(invoker).addFinallyListener(this::taskFinished);
        taskCount++;

        // if this object is cancelled this will be auto- cancelled by the SimpleTaskTracker
        tasks.trackForCancellation(future);

        return future;
    }

    private void taskFinished() {
        taskCount--;

        if (taskCount == 0) {
            invoker.invoke(() -> {
                if (taskCount == 0 && !done) {
                    done();
                }
            });
        }
    }

    private void done() {
        if (done) {
            return;
        }
        done = true;

        if (doneListener != null) {
            doneListener.run();
        }
    }

    public boolean isDone() {
        return done;
    }

    /** @return whether cancellation, rather than natural exhaustion, ended this tracker */
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * Makes this tracker and its children moot. This method is idempotent and
     * may be called from any thread; child cancellation is marshalled onto the
     * configured invoker when necessary.
     */
    @Override
    public synchronized void cancel() {
        if (cancelled) {
            return;
        }
        done = true;
        cancelled = true;
        if (invoker.isInvokerThread()) {
            tasks.cancel();
        } else {
            invoker.invoke(tasks::cancel);
        }
    }

    /**
     * Sets the callback for natural exhaustion of a non-empty task set.
     * Cancellation never invokes this listener.
     *
     * @param r callback run on the configured invoker
     * @return this tracker
     * @throws IllegalStateException if called off the invoker, after work has
     *         started, after completion, or more than once
     */
    public AsyncTaskTracker setDoneListener(Runnable r) {
        checkThread(invoker);
        if (taskCount > 0 || done) {
            throw new IllegalStateException("can't add listener, task is already in progress");
        }

        if (doneListener != null) {
            throw new IllegalStateException("Done listener already exists");
        }

        doneListener = r;

        return this;
    }
}

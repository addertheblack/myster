package com.general.thread;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * A completion context for a promise whose work consists of dynamically discovered child tasks.
 * Explicit result/exception publication completes the parent first, then cancels all owned work,
 * including already completed children. This preserves the promise framework's moot-cancellation
 * semantics: previously delivered listeners are not replayed, but later child listeners may see
 * cancellation. Cleanup never replaces the parent's published answer with cancellation.
 *
 * <p>Launch tasks and install the exhaustion listener on the configured invoker, unless the
 * listener is supplied during construction. Completion,
 * cancellation and cleanup-only registration may occur on any thread. Natural exhaustion invokes
 * its listener once after at least one counted task finishes; the listener must choose a fallback
 * result, exception or cancellation. No implicit result is manufactured for empty/exhausted work.
 *
 * @param <R> enclosing promise's result type
 */
public class AsyncTaskTracker<R> implements AsyncContext<R> {
    /** Creates a child-work owner and connects parent cancellation to its cleanup. */
    public static <R> AsyncTaskTracker<R> create(AsyncContext<R> context, Invoker invoker) {
        return create(context, invoker, Optional.empty());
    }

    /**
     * Creates a child-work owner and connects parent cancellation to its cleanup. May be called
     * from any thread. The optional exhaustion listener runs on the invoker and receives the
     * exhausted tracker so it can explicitly publish that tracker's outcome.
     */
    public static <R> AsyncTaskTracker<R> create(AsyncContext<R> context,
                                                Invoker invoker,
                                                Optional<Consumer<AsyncTaskTracker<R>>> doneListener) {
        AsyncTaskTracker<R> tracker = new AsyncTaskTracker<>(context, invoker);

        doneListener.ifPresent(l -> tracker.doneListener = l);
        context.trackForCancellation(tracker);
        return tracker;
    }

    private final AsyncContext<R> parent;
    private final Invoker invoker;
    private final SimpleTaskTracker children = new SimpleTaskTracker();
    private int taskCount;
    private volatile boolean terminal;
    private volatile boolean exhausted;
    private Consumer<AsyncTaskTracker<R>> doneListener;

    private AsyncTaskTracker(AsyncContext<R> parent, Invoker invoker) {
        this.parent = Objects.requireNonNull(parent, "parent");
        this.invoker = Objects.requireNonNull(invoker, "invoker");
    }

    private void checkThread() {
        if (!invoker.isInvokerThread()) {
            throw new IllegalStateException("AsyncTaskTracker operation called off the invoker thread: "
                    + Thread.currentThread().getName());
        }
    }

    /**
     * Starts one counted child and adapts its callbacks to this invoker. Its result does not
     * implicitly complete the parent; callbacks can publish through this tracker explicitly.
     * @throws IllegalStateException if called off the invoker or after termination/exhaustion
     */
    public <T> PromiseFuture<T> doAsync(AsyncCallable<T> callable) {
        checkThread();
        if (isDone()) {
            throw new IllegalStateException("Task is done");
        }
        PromiseFuture<T> future = callable.call().withInvoker(invoker);
        taskCount++;
        future.addFinallyListener(this::taskFinished);
        trackForCancellation(future);
        return future;
    }

    private void taskFinished() {
        if (terminal) {
            return;
        }
        taskCount--;
        if (taskCount == 0) {
            invoker.invoke(() -> {
                if (taskCount == 0 && !isDone()) {
                    exhausted = true;
                    if (doneListener != null) {
                        doneListener.accept(this);
                    }
                }
            });
        }
    }

    /**
     * Publishes one terminal outcome and then cancels every owned child. May be called from any
     * thread, including the exhaustion callback. Cancellation of children is invoker-confined.
     * @return whether the parent accepted this outcome
     */
    @Override
    public boolean setCallResult(CallResult<R> result) {
        Objects.requireNonNull(result, "result");
        synchronized (this) {
            if (terminal) {
                return false;
            }
            terminal = true;
        }
        try {
            return parent.setCallResult(result);
        } finally {
            if (invoker.isInvokerThread()) {
                children.cancel();
            } else {
                invoker.invoke(children::cancel);
            }
        }
    }

    /**
     * Owns resources without counting them toward natural exhaustion. Registration after explicit
     * completion or cancellation cancels them immediately. Register a deadline this way if it
     * must not delay an exhaustion fallback; use doAsync when the timer competes to produce a result.
     */
    @Override
    public void trackForCancellation(Cancellable... tasks) {
        synchronized (this) {
            if (!terminal) {
                children.trackForCancellation(tasks);
                return;
            }
        }
        for (Cancellable task : tasks) {
            task.cancel();
        }
    }

    /** @return whether this tracker has terminated or signalled natural exhaustion */
    public boolean isDone() {
        return terminal || exhausted;
    }

    /** @return whether the enclosing promise is cancelled, rather than successfully completed */
    @Override
    public boolean isCancelled() {
        return parent.isCancelled();
    }

    /** Cancels a pending parent and its children. Calling this after publication preserves its answer. */
    @Override
    public void cancel() {
        setCallResult(CallResult.createCancelled());
    }

    /**
     * Installs the natural-exhaustion callback, which receives this exhausted tracker and must
     * explicitly decide the parent's outcome.
     * Explicit completion/cancellation never invokes it. Zero launched tasks do not invoke it.
     * @throws IllegalStateException if called off the invoker, after work starts, or more than once
     */
    public AsyncTaskTracker<R> setDoneListener(Consumer<AsyncTaskTracker<R>> listener) {
        checkThread();
        if (taskCount > 0 || isDone() || doneListener != null) {
            throw new IllegalStateException("Exhaustion listener must be installed once before work starts");
        }
        doneListener = Objects.requireNonNull(listener, "listener");
        return this;
    }
}

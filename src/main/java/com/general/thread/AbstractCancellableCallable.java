package com.general.thread;

/**
 * Callable with cooperative cancellation. Subclasses check {@link #isCancelled()} before
 * starting more work; cancellation itself neither interrupts a thread nor closes resources.
 */
public abstract class AbstractCancellableCallable<T> implements CancellableCallable<T> {
    private volatile boolean cancelled;

    @Override
    public void cancel() {
        cancelled = true;
    }

    public final boolean isCancelled() {
        return cancelled;
    }
}

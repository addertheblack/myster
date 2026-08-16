package com.general.thread;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class TestCancellationTracking {
    @Test
    void contextCancellationCancelsTrackedTask() {
        AtomicBoolean taskCancelled = new AtomicBoolean();
        PromiseFuture<Void> future = PromiseFuture.newPromiseFuture(
                context -> context.trackForCancellation(() -> taskCancelled.set(true)));

        future.cancel();

        assertTrue(taskCancelled.get());
    }

    @Test
    void trackingAfterContextCancellationCancelsTaskImmediately() {
        AtomicReference<AsyncContext<Void>> contextReference = new AtomicReference<>();
        PromiseFuture<Void> future = PromiseFuture.newPromiseFuture(contextReference::set);
        AtomicBoolean taskCancelled = new AtomicBoolean();
        future.cancel();

        contextReference.get().trackForCancellation(() -> taskCancelled.set(true));

        assertTrue(taskCancelled.get());
    }

    @Test
    void trackingDoesNotForwardTaskCompletion() {
        AtomicReference<AsyncContext<String>> taskContext = new AtomicReference<>();
        PromiseFuture<String> task = PromiseFuture.newPromiseFuture(taskContext::set);
        PromiseFuture<Void> owner = PromiseFuture.newPromiseFuture(
                context -> context.trackForCancellation(task));

        taskContext.get().setResult("complete");

        assertTrue(task.isDone());
        assertFalse(owner.isDone());
    }

    @Test
    void simpleTrackerCancelsTrackedTasks() {
        SimpleTaskTracker tracker = new SimpleTaskTracker();
        AtomicBoolean taskCancelled = new AtomicBoolean();
        tracker.trackForCancellation(() -> taskCancelled.set(true));

        tracker.cancel();

        assertTrue(taskCancelled.get());
    }
}

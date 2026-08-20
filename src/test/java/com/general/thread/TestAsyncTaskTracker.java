package com.general.thread;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TestAsyncTaskTracker {
    private final Invoker invoker = Invoker.newVThreadInvoker();

    @AfterEach
    void shutDownInvoker() {
        invoker.shutdown();
    }

    @Test
    void cancellationFromAnyThreadIsVisibleAndDoesNotSignalNaturalCompletion() throws Exception {
        AtomicReference<AsyncTaskTracker> trackerReference = new AtomicReference<>();
        AtomicReference<PromiseFuture<String>> childReference = new AtomicReference<>();
        AtomicInteger doneCalls = new AtomicInteger();

        onInvoker(() -> {
            AsyncTaskTracker taskTracker =
                    AsyncTaskTracker.create(new SimpleTaskTracker(), invoker);
            taskTracker.setDoneListener(doneCalls::incrementAndGet);
            childReference.set(taskTracker.doAsync(() -> PromiseFuture.newPromiseFuture(_ -> {})));
            trackerReference.set(taskTracker);
        });

        trackerReference.get().cancel();

        assertTrue(trackerReference.get().isCancelled());
        assertTrue(trackerReference.get().isDone());
        invoker.waitForThread();
        invoker.waitForThread();
        assertTrue(childReference.get().isCancelled());
        assertEquals(0, doneCalls.get());
    }

    @Test
    void naturalCompletionSignalsDoneOnceEvenIfCancelledLater() throws Exception {
        AtomicReference<AsyncTaskTracker> trackerReference = new AtomicReference<>();
        AtomicReference<AsyncContext<String>> childContext = new AtomicReference<>();
        AtomicInteger doneCalls = new AtomicInteger();

        onInvoker(() -> {
            AsyncTaskTracker taskTracker =
                    AsyncTaskTracker.create(new SimpleTaskTracker(), invoker);
            taskTracker.setDoneListener(doneCalls::incrementAndGet);
            taskTracker.doAsync(() -> PromiseFuture.newPromiseFuture(childContext::set));
            trackerReference.set(taskTracker);
        });

        childContext.get().setResult("done");
        invoker.waitForThread();
        invoker.waitForThread();

        assertTrue(trackerReference.get().isDone());
        assertFalse(trackerReference.get().isCancelled());
        assertEquals(1, doneCalls.get());

        trackerReference.get().cancel();
        invoker.waitForThread();
        invoker.waitForThread();
        assertEquals(1, doneCalls.get());
    }

    @Test
    void doAsyncAdaptsFutureAlreadyAssignedToAnotherInvoker() throws Exception {
        Invoker originalInvoker = Invoker.newVThreadInvoker();
        try {
            AtomicReference<AsyncContext<String>> childContext = new AtomicReference<>();
            PromiseFuture<String> source =
                    PromiseFuture.newPromiseFuture(childContext::set).setInvoker(originalInvoker);
            AtomicReference<PromiseFuture<String>> trackedReference = new AtomicReference<>();
            AtomicInteger doneCalls = new AtomicInteger();

            onInvoker(() -> {
                AsyncTaskTracker taskTracker =
                        AsyncTaskTracker.create(new SimpleTaskTracker(), invoker);
                taskTracker.setDoneListener(doneCalls::incrementAndGet);
                trackedReference.set(taskTracker.doAsync(() -> source));
            });

            assertNotSame(source, trackedReference.get());
            assertSame(originalInvoker, source.getInvoker());
            assertSame(invoker, trackedReference.get().getInvoker());

            childContext.get().setResult("done");
            invoker.waitForThread();
            invoker.waitForThread();
            assertEquals(1, doneCalls.get());
        } finally {
            originalInvoker.shutdown();
        }
    }

    @Test
    void synchronousCallableFailureDoesNotStrandTaskCount() throws Exception {
        AtomicReference<AsyncContext<String>> childContext = new AtomicReference<>();
        AtomicInteger doneCalls = new AtomicInteger();

        onInvoker(() -> {
            AsyncTaskTracker taskTracker =
                    AsyncTaskTracker.create(new SimpleTaskTracker(), invoker);
            taskTracker.setDoneListener(doneCalls::incrementAndGet);
            assertThrows(IllegalStateException.class,
                         () -> taskTracker.doAsync(() -> {
                             throw new IllegalStateException("start failed");
                         }));
            taskTracker.doAsync(() -> PromiseFuture.newPromiseFuture(childContext::set));
        });

        childContext.get().setResult("done");
        invoker.waitForThread();
        invoker.waitForThread();
        assertEquals(1, doneCalls.get());
    }

    @Test
    void withInvokerReusesFutureWhenAssignmentIsCompatible() {
        PromiseFuture<String> future = PromiseFuture.newPromiseFuture(_ -> {});

        assertSame(future, future.withInvoker(invoker));
        assertSame(future, future.withInvoker(invoker));
    }

    @Test
    void withInvokerWrapperKeepsCancellationLinkedToSource() {
        Invoker originalInvoker = Invoker.newVThreadInvoker();
        try {
            PromiseFuture<String> source =
                    PromiseFuture.<String>newPromiseFuture(_ -> {}).setInvoker(originalInvoker);

            PromiseFuture<String> adapted = source.withInvoker(invoker);
            adapted.cancel();

            assertNotSame(source, adapted);
            assertTrue(source.isCancelled());
        } finally {
            originalInvoker.shutdown();
        }
    }

    private void onInvoker(Runnable runnable) throws Exception {
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        invoker.invoke(() -> {
            try {
                runnable.run();
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                finished.countDown();
            }
        });
        assertTrue(finished.await(5, TimeUnit.SECONDS));
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }
}

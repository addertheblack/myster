package com.general.thread;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class TestPromiseFuture {
    @Test
    void delayCompletesAfterMinimumDuration() throws Exception {
        long started = System.nanoTime();

        PromiseFutures.delay(Duration.ofMillis(30)).get();

        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        assertTrue(elapsedMillis >= 25, "delay completed after " + elapsedMillis + "ms");
    }

    @Test
    void cancellingDelayPublishesCancellationAndPreventsLaterCompletion() throws Exception {
        AtomicReference<CallResult<Void>> observed = new AtomicReference<>();
        PromiseFuture<Void> delayed = PromiseFutures.delay(Duration.ofMillis(100))
                .addSynchronousCallback(observed::set);

        delayed.cancel();
        Thread.sleep(150);

        assertTrue(delayed.isCancelled());
        assertTrue(observed.get().isCancelled());
    }

    @Test
    void ordinaryListenersRunInRegistrationOrderIncludingFinallyListeners() {
        AtomicReference<AsyncContext<String>> contextReference = new AtomicReference<>();
        List<String> calls = new ArrayList<>();
        PromiseFuture<String> future = PromiseFuture
                .newPromiseFuture(contextReference::set)
                .setInvoker(Invoker.SYNCHRONOUS);

        future.addFinallyListener(() -> calls.add("first finally"))
                .addResultListener(_ -> calls.add("first result"))
                .addResultListener(_ -> calls.add("second result"))
                .addFinallyListener(() -> calls.add("last finally"));

        contextReference.get().setResult("done");

        assertEquals(List.of(
                "first finally",
                "first result",
                "second result",
                "last finally"), calls);
    }

    @Test
    void mapAsyncCancellationCancelsPendingSourceAndSkipsMapper() {
        AtomicReference<AsyncContext<String>> sourceContext = new AtomicReference<>();
        AtomicBoolean mapperCalled = new AtomicBoolean();
        PromiseFuture<String> source = PromiseFuture.newPromiseFuture(sourceContext::set);
        PromiseFuture<Integer> mapped = source.mapAsyncInline(value -> {
            mapperCalled.set(true);
            return PromiseFuture.newPromiseFuture(value.length());
        });

        mapped.cancel();
        sourceContext.get().setResult("late result");

        assertTrue(mapped.isCancelled());
        assertTrue(source.isCancelled());
        assertFalse(mapperCalled.get());
    }

    @Test
    void mapAsyncSourceCancellationSkipsMapperAndCancelsReturnedFuture() {
        AtomicBoolean mapperCalled = new AtomicBoolean();
        PromiseFuture<String> source = PromiseFuture.newPromiseFuture(_ -> {});
        PromiseFuture<Integer> mapped = source.mapAsyncInline(value -> {
            mapperCalled.set(true);
            return PromiseFuture.newPromiseFuture(value.length());
        });

        source.cancel();

        assertTrue(mapped.isCancelled());
        assertFalse(mapperCalled.get());
    }

    @Test
    void mapAsyncSourceExceptionSkipsMapperAndForwardsException() {
        AtomicReference<AsyncContext<String>> sourceContext = new AtomicReference<>();
        AtomicBoolean mapperCalled = new AtomicBoolean();
        IllegalStateException failure = new IllegalStateException("source failed");
        PromiseFuture<String> source = PromiseFuture.newPromiseFuture(sourceContext::set);
        PromiseFuture<Integer> mapped = source.mapAsyncInline(value -> {
            mapperCalled.set(true);
            return PromiseFuture.newPromiseFuture(value.length());
        });

        sourceContext.get().setException(failure);

        ExecutionException thrown = assertThrows(ExecutionException.class, mapped::get);
        assertSame(failure, thrown.getCause());
        assertFalse(mapperCalled.get());
    }

    @Test
    void mapAsyncForwardsMappedResult() throws Exception {
        AtomicReference<AsyncContext<String>> sourceContext = new AtomicReference<>();
        AtomicReference<AsyncContext<Integer>> mappedContext = new AtomicReference<>();
        AtomicReference<String> mappedValue = new AtomicReference<>();
        PromiseFuture<String> source = PromiseFuture.newPromiseFuture(sourceContext::set);
        PromiseFuture<Integer> mapped = source.mapAsyncInline(value -> {
            mappedValue.set(value);
            return PromiseFuture.newPromiseFuture(mappedContext::set);
        });

        sourceContext.get().setResult("source result");
        mappedContext.get().setResult(42);

        assertEquals("source result", mappedValue.get());
        assertEquals(42, mapped.get());
    }

    @Test
    void mapAsyncCancellationAfterMappingStartsCancelsBothStages() {
        AtomicReference<AsyncContext<String>> sourceContext = new AtomicReference<>();
        AtomicReference<PromiseFuture<Integer>> mappedStage = new AtomicReference<>();
        PromiseFuture<String> source = PromiseFuture.newPromiseFuture(sourceContext::set);
        PromiseFuture<Integer> mapped = source.mapAsyncInline(_ -> {
            PromiseFuture<Integer> future = PromiseFuture.newPromiseFuture(_ -> {});
            mappedStage.set(future);
            return future;
        });
        sourceContext.get().setResult("source result");

        mapped.cancel();

        assertTrue(mapped.isCancelled());
        assertTrue(source.isCancelled());
        assertTrue(mappedStage.get().isCancelled());
    }

    @Test
    void mapAsyncInvokerControlsWhereMapperIsInvoked() throws Exception {
        AtomicReference<Runnable> queuedMapper = new AtomicReference<>();
        AtomicBoolean mapperCalled = new AtomicBoolean();
        Invoker mapperInvoker = recordingInvoker(queuedMapper);

        PromiseFuture<Integer> mapped = PromiseFuture.newPromiseFuture("source")
                .mapAsync(value -> {
                    mapperCalled.set(true);
                    return PromiseFuture.newPromiseFuture(value.length());
                }, mapperInvoker);

        assertFalse(mapperCalled.get());
        queuedMapper.get().run();

        assertTrue(mapperCalled.get());
        assertEquals(6, mapped.get());
        assertNull(mapped.getInvoker());
    }

    @Test
    void mapAsyncCancellationBeforeInvokerDispatchSkipsMapper() {
        AtomicReference<Runnable> queuedMapper = new AtomicReference<>();
        AtomicBoolean mapperCalled = new AtomicBoolean();
        PromiseFuture<String> source = PromiseFuture.newPromiseFuture("source");
        PromiseFuture<Integer> mapped = source.mapAsync(value -> {
            mapperCalled.set(true);
            return PromiseFuture.newPromiseFuture(value.length());
        }, recordingInvoker(queuedMapper));

        mapped.cancel();
        queuedMapper.get().run();

        assertTrue(mapped.isCancelled());
        assertTrue(source.isCancelled());
        assertFalse(mapperCalled.get());
    }

    @Test
    void concurrentSourceAndMappedCancellationDoNotDeadlock() throws Exception {
        CountDownLatch sourceCallbackEntered = new CountDownLatch(1);
        CountDownLatch mappedCallbackEntered = new CountDownLatch(1);
        PromiseFuture<String> source = PromiseFuture.newPromiseFuture(_ -> {});
        source.addSynchronousCallback(_ -> {
            sourceCallbackEntered.countDown();
            await(mappedCallbackEntered);
        });
        PromiseFuture<Integer> mapped = source.mapAsyncInline(
                _ -> PromiseFuture.newPromiseFuture(42));
        mapped.addSynchronousCallback(_ -> {
            mappedCallbackEntered.countDown();
            await(sourceCallbackEntered);
        });

        Thread sourceCancellation = Thread.ofVirtual().start(source::cancel);
        Thread mappedCancellation = Thread.ofVirtual().start(mapped::cancel);

        assertTrue(sourceCancellation.join(Duration.ofSeconds(2)));
        assertTrue(mappedCancellation.join(Duration.ofSeconds(2)));
        assertTrue(source.isCancelled());
        assertTrue(mapped.isCancelled());
    }

    private static void await(CountDownLatch latch) {
        boolean interrupted = false;
        while (true) {
            try {
                latch.await();
                break;
            } catch (InterruptedException _) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static Invoker recordingInvoker(AtomicReference<Runnable> queuedTask) {
        return new Invoker() {
            @Override
            public void invoke(Runnable runnable) {
                queuedTask.set(runnable);
            }

            @Override
            public boolean isInvokerThread() {
                return false;
            }

            @Override
            public void shutdown() {}
        };
    }
}

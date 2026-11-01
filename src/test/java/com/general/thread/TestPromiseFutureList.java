package com.general.thread;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

class TestPromiseFutureList {
    @Test
    void replaysResultsCompletedBeforeInvokerIsAssigned() throws Exception {
        PromiseFutureList<String> future = PromiseFutureList.newPromiseFutureList(context -> {
            assertTrue(context.addResult("first"));
            assertTrue(context.addResult("second"));
            context.done();
        });
        List<String> received = new ArrayList<>();
        List<Boolean> onEdt = new ArrayList<>();

        assertEquals(List.of("first", "second"), future.get(5, TimeUnit.SECONDS));
        future.useEdt().addPartialResultListener(value -> {
            received.add(value);
            onEdt.add(SwingUtilities.isEventDispatchThread());
        });
        SwingUtilities.invokeAndWait(() -> {});

        assertEquals(List.of("first", "second"), received);
        assertEquals(List.of(true, true), onEdt);
    }

    @Test
    void deliversBufferedAndLiveResultsOnceInOrder() throws Exception {
        AtomicReference<AsyncContextList<String>> contextReference = new AtomicReference<>();
        PromiseFutureList<String> future = PromiseFutureList.newPromiseFutureList(contextReference::set);
        AsyncContextList<String> context = contextReference.get();
        List<String> received = new ArrayList<>();
        List<Boolean> onEdt = new ArrayList<>();

        assertTrue(context.addResult("before invoker"));
        future.useEdt();
        assertTrue(context.addResult("before listener"));
        future.addPartialResultListener(value -> {
            received.add(value);
            onEdt.add(SwingUtilities.isEventDispatchThread());
        });
        assertTrue(context.addResult("after listener"));
        context.done();
        List<String> expected = List.of("before invoker", "before listener", "after listener");
        assertEquals(expected, future.get(5, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> {});

        assertEquals(expected, received);
        assertEquals(List.of(true, true, true), onEdt);
    }
}

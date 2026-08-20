package com.general.thread;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class TestPromiseFuture {
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
}

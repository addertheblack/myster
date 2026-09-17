package com.myster.net.stream.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.general.thread.Invoker;
import com.general.thread.PromiseFutureList;
import com.myster.mml.MessagePak;
import com.myster.net.DisconnectException;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.search.MysterFileStub;
import com.myster.type.MysterType;

class TestFileStatsBatch {
    private final MysterDataInputStream input = mock(MysterDataInputStream.class);
    private final ByteArrayOutputStream requests = new ByteArrayOutputStream();
    private final MysterSocket socket = mock(MysterSocket.class,
            withSettings().useConstructor(input, new MysterDataOutputStream(requests)));
    private final Invoker invoker = Invoker.newVThreadInvoker();
    private final CountDownLatch allowReads = new CountDownLatch(1);
    private final CountDownLatch readStarted = new CountDownLatch(1);
    private final AtomicReference<Thread> worker = new AtomicReference<>();
    private PromiseFutureList<MessagePak> future;

    @AfterEach
    void stopWorker() throws Exception {
        try {
            if (future != null && !future.isDone()) {
                future.cancel();
            }
            allowReads.countDown();
            if (worker.get() != null) {
                assertTrue(worker.get().join(Duration.ofSeconds(5)), "Batch worker did not stop");
            }
        } finally {
            invoker.shutdown();
        }
    }

    @Test
    void deliversOrderedResponsesIncrementallyOnInvoker() throws Exception {
        MysterFileStub[] stubs = stubs(35);
        List<MessagePak> received = new ArrayList<>();
        BlockingQueue<MessagePak> partialResults = new LinkedBlockingQueue<>();
        BlockingQueue<Boolean> onInvoker = new LinkedBlockingQueue<>();
        Semaphore nextResponse = new Semaphore(0);
        AtomicInteger responseIndex = new AtomicInteger();
        when(input.readByte()).thenReturn((byte) 1);
        when(input.readMessagePack()).thenAnswer(_ -> {
            if (!nextResponse.tryAcquire(5, TimeUnit.SECONDS)) {
                throw new IOException("Timed out waiting to send the next response");
            }
            MessagePak result = MessagePak.newEmpty();
            result.putInt("/index", responseIndex.getAndIncrement());
            return result;
        });

        startBatch(stubs).addPartialResultListener(result -> {
            onInvoker.add(invoker.isInvokerThread());
            partialResults.add(result);
        });
        allowReads.countDown();

        for (int i = 0; i < stubs.length; i++) {
            nextResponse.release();
            MessagePak result = partialResults.poll(5, TimeUnit.SECONDS);
            assertNotNull(result, "Partial result was not delivered");
            assertEquals(i, result.getInt("/index").orElseThrow());
            assertEquals(Boolean.TRUE, onInvoker.poll(5, TimeUnit.SECONDS));
            received.add(result);
            if (i < stubs.length - 1) {
                assertFalse(future.isDone());
            }
        }

        assertEquals(received, future.get(5, TimeUnit.SECONDS));
        MysterDataInputStream sent = new MysterDataInputStream(
                new ByteArrayInputStream(requests.toByteArray()));
        for (int i = 0; i < stubs.length; i++) {
            assertEquals(i, received.get(i).getInt("/index").orElseThrow());
            assertEquals(77, sent.readInt());
            assertEquals(stubs[i].getType(), sent.readType());
            assertEquals(stubs[i].getName(), sent.readUTF());
        }
        assertEquals(-1, sent.read());
    }

    @Test
    void reportsProtocolFailureAfterDeliveringEarlierResults() throws Exception {
        MessagePak first = MessagePak.newEmpty();
        BlockingQueue<MessagePak> received = new LinkedBlockingQueue<>();
        CountDownLatch callbacksDone = new CountDownLatch(1);
        when(input.readByte()).thenReturn((byte) 1, (byte) 0);
        when(input.readMessagePack()).thenReturn(first);

        startBatch(stubs(2)).addPartialResultListener(received::add)
                .addFinallyListener(callbacksDone::countDown);
        allowReads.countDown();

        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> future.get(5, TimeUnit.SECONDS));

        assertInstanceOf(DisconnectException.class, failure.getCause());
        assertTrue(callbacksDone.await(5, TimeUnit.SECONDS));
        assertEquals(List.of(first), new ArrayList<>(received));
    }

    @Test
    void reportsOriginalIoFailureThroughFuture() throws Exception {
        IOException ioFailure = new IOException("connection closed");
        when(input.readByte()).thenThrow(ioFailure);
        startBatch(stubs(1));
        allowReads.countDown();

        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> future.get(5, TimeUnit.SECONDS));

        assertSame(ioFailure, failure.getCause());
    }

    @Test
    void cancellationStopsFurtherRequestsAndResponses() throws Exception {
        MysterFileStub[] stubs = stubs(35);
        startBatch(stubs);
        assertTrue(readStarted.await(5, TimeUnit.SECONDS));

        future.cancel();
        allowReads.countDown();
        assertTrue(worker.get().join(Duration.ofSeconds(5)), "Batch worker did not stop");

        assertTrue(future.isCancelled());
        assertThrows(CancellationException.class, () -> future.get(5, TimeUnit.SECONDS));
        verify(input, never()).readByte();
        verify(input, never()).readMessagePack();
        MysterDataInputStream sent = new MysterDataInputStream(
                new ByteArrayInputStream(requests.toByteArray()));
        assertEquals(77, sent.readInt());
        assertEquals(stubs[0].getType(), sent.readType());
        assertEquals(stubs[0].getName(), sent.readUTF());
        assertEquals(-1, sent.read());
    }

    private PromiseFutureList<MessagePak> startBatch(MysterFileStub[] stubs) throws IOException {
        // Hold the worker until the test has installed the invoker and listeners.
        when(input.available()).thenAnswer(_ -> {
            worker.set(Thread.currentThread());
            readStarted.countDown();
            if (!allowReads.await(5, TimeUnit.SECONDS)) {
                throw new IOException("Timed out waiting to read responses");
            }
            return 0;
        });
        future = StandardSuiteStream.getFileStatsBatch(socket, stubs).setInvoker(invoker);
        return future;
    }

    private MysterFileStub[] stubs(int count) {
        MysterAddress address = new MysterAddress(InetAddress.getLoopbackAddress());
        MysterType type = new MysterType(new byte[16]);
        MysterFileStub[] stubs = new MysterFileStub[count];
        for (int i = 0; i < count; i++) {
            stubs[i] = new MysterFileStub(address, type, "file-" + i);
        }
        return stubs;
    }
}

package com.myster.net.stream.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

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

    @Test
    void deliversOrderedResponsesIncrementallyOnCallingThread() throws Exception {
        MysterFileStub[] stubs = stubs(35);
        Thread caller = Thread.currentThread();
        List<MessagePak> received = new ArrayList<>();
        when(input.readByte()).thenReturn((byte) 1);
        when(input.readMessagePack()).thenAnswer(_ -> {
            // A response must be delivered before reading the next one.
            MessagePak result = MessagePak.newEmpty();
            result.putInt("/index", received.size());
            return result;
        });

        StandardSuiteStream.getFileStatsBatch(socket, stubs, result -> {
            assertSame(caller, Thread.currentThread());
            received.add(result);
        });

        assertEquals(stubs.length, received.size());
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
        List<MessagePak> received = new ArrayList<>();
        when(input.readByte()).thenReturn((byte) 1, (byte) 0);
        when(input.readMessagePack()).thenReturn(first);

        assertThrows(DisconnectException.class,
                () -> StandardSuiteStream.getFileStatsBatch(socket, stubs(2), received::add));

        assertEquals(List.of(first), received);
    }

    @Test
    void forwardsIoAndListenerFailuresWithoutWrapping() throws Exception {
        IOException ioFailure = new IOException("connection closed");
        when(input.readByte()).thenThrow(ioFailure);
        assertSame(ioFailure, assertThrows(IOException.class,
                () -> StandardSuiteStream.getFileStatsBatch(socket, stubs(1), _ -> {})));

        doReturn((byte) 1).when(input).readByte();
        when(input.readMessagePack()).thenReturn(MessagePak.newEmpty());
        RuntimeException listenerFailure = new IllegalStateException("listener failed");
        assertSame(listenerFailure, assertThrows(IllegalStateException.class,
                () -> StandardSuiteStream.getFileStatsBatch(socket, stubs(1), _ -> {
                    throw listenerFailure;
                })));
    }

    @Test
    void interruptionStopsRequestsAndPreservesInterruptStatus() throws Exception {
        Thread.currentThread().interrupt();
        try {
            assertThrows(InterruptedIOException.class,
                    () -> StandardSuiteStream.getFileStatsBatch(socket, stubs(1), _ -> {}));
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(0, requests.size());
        } finally {
            Thread.interrupted();
        }
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

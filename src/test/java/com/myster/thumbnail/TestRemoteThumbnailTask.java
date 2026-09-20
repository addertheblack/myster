package com.myster.thumbnail;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.general.thread.PromiseFutures;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.net.client.MysterStream;
import com.myster.type.MysterType;

@Timeout(10)
class TestRemoteThumbnailTask {
    private final MysterStream stream = mock(MysterStream.class);
    private final Object transferLock = new Object();
    private final MysterAddress address = MysterAddress.createMysterAddress("127.0.0.1:6669");
    private final MysterType type = new MysterType(new byte[16]);

    TestRemoteThumbnailTask() throws Exception {}

    private RemoteThumbnailTask task(String filename) {
        return new RemoteThumbnailTask(stream, transferLock, address, type, filename, 128);
    }

    @Test
    void forwardsRequestAndClosesSocketOnSuccessMissAndFailure() throws Exception {
        MysterSocket socket = mock(MysterSocket.class);
        BufferedImage image = new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB);
        when(stream.makeStreamConnection(address)).thenAnswer(_ -> {
            assertFalse(SwingUtilities.isEventDispatchThread());
            return socket;
        });
        when(stream.getThumbnail(socket, type, "été/写真.jpg", 128))
                .thenReturn(image, null).thenThrow(new IOException("broken body"));

        assertSame(image, PromiseFutures.execute(task("été/写真.jpg")).get(5, TimeUnit.SECONDS));
        assertNull(PromiseFutures.execute(task("été/写真.jpg")).get(5, TimeUnit.SECONDS));
        assertInstanceOf(IOException.class, assertThrows(java.util.concurrent.ExecutionException.class,
                () -> PromiseFutures.execute(task("été/写真.jpg")).get(5, TimeUnit.SECONDS)).getCause());
        verify(socket, times(3)).close();
    }

    @Test
    void cancelledTaskWaitingForMonitorNeverConnects() throws Exception {
        RemoteThumbnailTask task = task("cancelled");
        FutureTask<BufferedImage> result = new FutureTask<>(task);
        Thread worker = Thread.ofPlatform().unstarted(result);
        synchronized (transferLock) {
            worker.start();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (worker.getState() != Thread.State.BLOCKED && System.nanoTime() < deadline) {
                Thread.sleep(1);
            }
            assertEquals(Thread.State.BLOCKED, worker.getState());
            task.cancel();
        }
        assertInstanceOf(CancellationException.class,
                assertThrows(java.util.concurrent.ExecutionException.class,
                        () -> result.get(5, TimeUnit.SECONDS)).getCause());
        verifyNoInteractions(stream);
    }

    @Test
    void cancellationDuringConnectSkipsRequestAndClosesOnWorker() throws Exception {
        MysterSocket socket = mock(MysterSocket.class);
        CountDownLatch connecting = new CountDownLatch(1);
        CountDownLatch releaseConnect = new CountDownLatch(1);
        CountDownLatch closed = new CountDownLatch(1);
        when(stream.makeStreamConnection(address)).thenAnswer(_ -> {
            connecting.countDown();
            assertTrue(releaseConnect.await(5, TimeUnit.SECONDS));
            return socket;
        });
        doAnswer(_ -> {
            assertFalse(SwingUtilities.isEventDispatchThread());
            closed.countDown();
            return null;
        }).when(socket).close();
        var future = PromiseFutures.execute(task("connecting"));
        try {
            assertTrue(connecting.await(5, TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(future::cancel);
            assertTrue(future.isCancelled());
        } finally {
            releaseConnect.countDown();
        }
        assertTrue(closed.await(5, TimeUnit.SECONDS));
        verify(stream, never()).getThumbnail(any(), any(), anyString(), anyInt());
    }

    @Test
    void cancelledReadKeepsReplacementOutUntilSocketCleanupFinishes() throws Exception {
        MysterSocket firstSocket = mock(MysterSocket.class);
        MysterSocket secondSocket = mock(MysterSocket.class);
        CountDownLatch reading = new CountDownLatch(1);
        CountDownLatch finishRead = new CountDownLatch(1);
        CountDownLatch closing = new CountDownLatch(1);
        CountDownLatch finishClose = new CountDownLatch(1);
        CountDownLatch replacementConnected = new CountDownLatch(1);
        when(stream.makeStreamConnection(address)).thenReturn(firstSocket).thenAnswer(_ -> {
            replacementConnected.countDown();
            return secondSocket;
        });
        when(stream.getThumbnail(firstSocket, type, "old", 128)).thenAnswer(_ -> {
            reading.countDown();
            assertTrue(finishRead.await(5, TimeUnit.SECONDS));
            return null;
        });
        doAnswer(_ -> {
            closing.countDown();
            assertTrue(finishClose.await(5, TimeUnit.SECONDS));
            return null;
        }).when(firstSocket).close();

        var first = PromiseFutures.execute(task("old"));
        try {
            assertTrue(reading.await(5, TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(first::cancel);
            var second = PromiseFutures.execute(task("new"));
            assertFalse(replacementConnected.await(100, TimeUnit.MILLISECONDS));
            finishRead.countDown();
            assertTrue(closing.await(5, TimeUnit.SECONDS));
            assertFalse(replacementConnected.await(100, TimeUnit.MILLISECONDS));
            finishClose.countDown();
            assertNull(second.get(5, TimeUnit.SECONDS));
            verify(stream).getThumbnail(secondSocket, type, "new", 128);
        } finally {
            finishRead.countDown();
            finishClose.countDown();
        }
    }
}

package com.myster.thumbnail;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.net.client.MysterStream;
import com.myster.net.stream.client.UnknownProtocolException;
import com.myster.thumbnail.RemoteThumbnailCache.Request;
import com.myster.type.MysterType;

@Timeout(10)
class TestRemoteThumbnailCache {
    private final MysterStream stream = mock(MysterStream.class);
    private final MysterSocket socket = mock(MysterSocket.class);
    private final MysterAddress address = MysterAddress.createMysterAddress("127.0.0.1:6669");
    private final MysterType type = new MysterType(new byte[16]);
    private final List<BufferedImage> delivered = new ArrayList<>();
    private final AtomicReference<Thread> worker = new AtomicReference<>();
    private RemoteThumbnailCache thumbnailCache;

    TestRemoteThumbnailCache() throws Exception {}

    @BeforeEach
    void setup() throws Exception {
        when(stream.makeStreamConnection(address)).thenAnswer(_ -> {
            worker.set(Thread.currentThread());
            return socket;
        });
        SwingUtilities.invokeAndWait(() -> thumbnailCache = new RemoteThumbnailCache(stream, image -> {
            assertTrue(SwingUtilities.isEventDispatchThread());
            delivered.add(image);
        }));
    }

    @AfterEach
    void close() throws Exception {
        SwingUtilities.invokeAndWait(thumbnailCache::close);
    }

    private Request request(String filename, int size) {
        return new Request(address, type, filename, size);
    }

    private void load(Request request) throws Exception {
        worker.set(null);
        CountDownLatch closed = new CountDownLatch(1);
        doAnswer(_ -> { closed.countDown(); return null; }).when(socket).close();
        SwingUtilities.invokeAndWait(() -> thumbnailCache.replace(Optional.of(request)));
        assertTrue(closed.await(5, TimeUnit.SECONDS));
        worker.get().join(5000);
        assertFalse(worker.get().isAlive());
        SwingUtilities.invokeAndWait(() -> {});
    }

    @Test
    void reusesSmallestLargerImage() throws Exception {
        BufferedImage small = new BufferedImage(128, 64, BufferedImage.TYPE_INT_ARGB);
        BufferedImage large = new BufferedImage(256, 128, BufferedImage.TYPE_INT_ARGB);
        when(stream.getThumbnail(socket, type, "image", 128)).thenReturn(small);
        when(stream.getThumbnail(socket, type, "image", 256)).thenReturn(large);
        load(request("image", 128));
        load(request("image", 256));
        SwingUtilities.invokeAndWait(() -> {
            thumbnailCache.replace(Optional.of(request("image", 64)));
            thumbnailCache.replace(Optional.of(request("image", 200)));
        });
        assertEquals(List.of(small, large, small, large), delivered);
        verify(stream, times(2)).makeStreamConnection(address);
    }

    @Test
    void replacementDiscardsOldResultAndDoesNotCacheIt() throws Exception {
        BufferedImage oldImage = new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB);
        BufferedImage newImage = new BufferedImage(1, 2, BufferedImage.TYPE_INT_ARGB);
        CountDownLatch reading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch newRead = new CountDownLatch(1);
        when(stream.getThumbnail(socket, type, "old", 128)).thenAnswer(_ -> {
            reading.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return oldImage;
        });
        when(stream.getThumbnail(socket, type, "new", 128)).thenAnswer(_ -> {
            newRead.countDown();
            return newImage;
        });
        SwingUtilities.invokeAndWait(() -> {
            thumbnailCache.replace(Optional.of(request("old", 128)));
            thumbnailCache.replace(Optional.of(request("old", 128)));
        });
        assertTrue(reading.await(5, TimeUnit.SECONDS));
        try {
            SwingUtilities.invokeAndWait(() -> {
                thumbnailCache.replace(Optional.of(request("queued", 128)));
                thumbnailCache.replace(Optional.of(request("new", 128)));
            });
            release.countDown();
            assertTrue(newRead.await(5, TimeUnit.SECONDS));
            worker.get().join(5000);
            assertFalse(worker.get().isAlive());
            SwingUtilities.invokeAndWait(() -> {});
        } finally {
            release.countDown();
        }
        assertEquals(List.of(newImage), delivered);
        verify(stream, never()).getThumbnail(socket, type, "queued", 128);
        load(request("old", 128));
        verify(stream, times(2)).getThumbnail(socket, type, "old", 128);
    }

    @Test
    void resetBeforeEdtDeliveryDiscardsCompletedResultEvenForSameRequest() throws Exception {
        BufferedImage image = new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB);
        when(stream.getThumbnail(socket, type, "image", 128)).thenReturn(image);
        CountDownLatch connected = new CountDownLatch(1);
        when(stream.makeStreamConnection(address)).thenAnswer(_ -> {
            worker.set(Thread.currentThread());
            connected.countDown();
            return socket;
        });
        SwingUtilities.invokeAndWait(() -> {
            thumbnailCache.replace(Optional.of(request("image", 128)));
            try {
                assertTrue(connected.await(5, TimeUnit.SECONDS));
                worker.get().join(5000);
            } catch (InterruptedException exception) {
                throw new AssertionError(exception);
            }
            thumbnailCache.reset();
        });
        SwingUtilities.invokeAndWait(() -> {});
        assertTrue(delivered.isEmpty());
        load(request("image", 128));
        assertEquals(List.of(image), delivered);
        verify(stream, times(2)).makeStreamConnection(address);
    }

    @Test
    void suppressesMissesFailuresAndUnsupportedEndpointUntilReset() throws Exception {
        when(stream.getThumbnail(socket, type, "error", 128)).thenThrow(new IOException("offline"));
        when(stream.getThumbnail(socket, type, "unsupported", 128)).thenThrow(new UnknownProtocolException(0));
        load(request("missing", 128));
        load(request("error", 128));
        SwingUtilities.invokeAndWait(() -> {
            thumbnailCache.replace(Optional.of(request("missing", 128)));
            thumbnailCache.replace(Optional.of(request("error", 128)));
        });
        verify(stream, times(2)).makeStreamConnection(address);
        load(request("unsupported", 128));
        SwingUtilities.invokeAndWait(() -> thumbnailCache.replace(Optional.of(request("another file", 256))));
        verify(stream, times(3)).makeStreamConnection(address);
        assertTrue(delivered.isEmpty());
        SwingUtilities.invokeAndWait(thumbnailCache::reset);
        load(request("missing", 128));
        verify(stream, times(4)).makeStreamConnection(address);
    }

    @Test
    void evictsLeastRecentlyUsedImagesAtByteAndEntryBounds() throws Exception {
        BufferedImage large = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        when(stream.getThumbnail(eq(socket), eq(type), anyString(), eq(256))).thenReturn(large);
        for (int i = 0; i < 33; i++) {
            load(request("image" + i, 256));
        }
        load(request("image0", 256));
        verify(stream, times(2)).getThumbnail(socket, type, "image0", 256);
        SwingUtilities.invokeAndWait(thumbnailCache::reset);
        for (int i = 0; i < 129; i++) {
            load(request("miss" + i, 128));
        }
        load(request("miss0", 128));
        verify(stream, times(2)).getThumbnail(socket, type, "miss0", 128);
    }

    @Test
    void slowTransferDoesNotBlockAnotherCache() throws Exception {
        MysterAddress otherAddress = MysterAddress.createMysterAddress("127.0.0.1:6670");
        MysterSocket otherSocket = mock(MysterSocket.class);
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        CountDownLatch reading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch otherLoaded = new CountDownLatch(1);
        AtomicReference<RemoteThumbnailCache> otherCache = new AtomicReference<>();
        when(stream.getThumbnail(socket, type, "slow", 128)).thenAnswer(_ -> {
            reading.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return null;
        });
        when(stream.makeStreamConnection(otherAddress)).thenReturn(otherSocket);
        when(stream.getThumbnail(otherSocket, type, "fast", 128)).thenReturn(image);
        try {
            SwingUtilities.invokeAndWait(() -> thumbnailCache.replace(Optional.of(request("slow", 128))));
            assertTrue(reading.await(5, TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(() -> {
                otherCache.set(new RemoteThumbnailCache(stream, _ -> otherLoaded.countDown()));
                otherCache.get().replace(Optional.of(new Request(otherAddress, type, "fast", 128)));
            });
            assertTrue(otherLoaded.await(2, TimeUnit.SECONDS), "another cache must not wait for the slow read");
            verify(otherSocket).close();
        } finally {
            release.countDown();
            if (worker.get() != null) worker.get().join(5000);
            SwingUtilities.invokeAndWait(() -> {
                if (otherCache.get() != null) otherCache.get().close();
            });
        }
    }

    @Test
    void resetRetainsLockUntilCancelledTransferFinishes() throws Exception {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        CountDownLatch reading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch replacementConnected = new CountDownLatch(1);
        when(stream.makeStreamConnection(address)).thenAnswer(_ -> {
            worker.set(Thread.currentThread());
            return socket;
        }).thenAnswer(_ -> {
            worker.set(Thread.currentThread());
            replacementConnected.countDown();
            return socket;
        });
        when(stream.getThumbnail(socket, type, "old", 128)).thenAnswer(_ -> {
            reading.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return image;
        });
        when(stream.getThumbnail(socket, type, "new", 128)).thenReturn(image);
        try {
            SwingUtilities.invokeAndWait(() -> thumbnailCache.replace(Optional.of(request("old", 128))));
            assertTrue(reading.await(5, TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(() -> {
                thumbnailCache.reset();
                thumbnailCache.replace(Optional.of(request("new", 128)));
            });
            assertFalse(replacementConnected.await(100, TimeUnit.MILLISECONDS));
            release.countDown();
            assertTrue(replacementConnected.await(5, TimeUnit.SECONDS));
            worker.get().join(5000);
            SwingUtilities.invokeAndWait(() -> {});
            assertEquals(List.of(image), delivered);
        } finally {
            release.countDown();
        }
    }

    @Test
    void closeIsTerminal() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            thumbnailCache.close();
            thumbnailCache.replace(Optional.of(request("image", 128)));
        });
        verifyNoInteractions(stream);
    }
}

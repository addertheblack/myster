package com.myster.thumbnail;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestThumbnails {
    @TempDir
    Path directory;

    @Test
    void fitsLandscapePortraitAndExtremeAspectRatioWithoutUpscaling() {
        for (int size : new int[] { 16, 32, 64 }) {
            BufferedImage landscape = ThumbnailImageUtils.fit(image(400, 200), size);
            assertEquals(size, landscape.getWidth());
            assertEquals(size / 2, landscape.getHeight());
            BufferedImage portrait = ThumbnailImageUtils.fit(image(200, 400), size);
            assertEquals(size / 2, portrait.getWidth());
            assertEquals(size, portrait.getHeight());
            BufferedImage panorama = ThumbnailImageUtils.fit(image(10000, 1), size);
            assertEquals(size, panorama.getWidth());
            assertEquals(1, panorama.getHeight());
        }
        BufferedImage small = image(4, 8);
        assertSame(small, ThumbnailImageUtils.fit(small, 64));
    }

    @Test
    void validatesArgumentsAndRejectsBlockingEdtCalls() throws Exception {
        Path file = directory.resolve("file.jpg");
        assertThrows(NullPointerException.class, () -> Thumbnails.summonThumbnailAsync(null, 32));
        for (int size : new int[] { -1, 0, 1025, Integer.MAX_VALUE }) {
            assertThrows(IllegalArgumentException.class,
                         () -> Thumbnails.summonThumbnailAsync(file, size));
        }
        SwingUtilities.invokeAndWait(() -> assertThrows(IllegalStateException.class,
                () -> Thumbnails.summonThumbnail(file, 32)));
    }

    @Test
    void asyncIsCallableOnEdtAndMissingFileReturnsNoThumbnail() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        SwingUtilities.invokeAndWait(() -> Thumbnails.summonThumbnailAsync(directory.resolve("missing.jpg"), 32)
                .useEdt().addResultListener(result -> {
                    assertTrue(SwingUtilities.isEventDispatchThread());
                    assertNull(result);
                    done.countDown();
                }).addExceptionListener(error -> fail(error)));
        assertTrue(done.await(5, TimeUnit.SECONDS));
    }

    @Test
    void reusesCacheButInvalidatesOnSizeModificationAndReplacement() throws Exception {
        Path file = Files.writeString(directory.resolve("file.jpg"), "original");
        ThumbnailProvider provider = mock(ThumbnailProvider.class);
        when(provider.load(any(), anyInt())).thenAnswer(_ -> Optional.of(image(400, 200)));
        ThumbnailService service = new ThumbnailService(provider);
        BufferedImage first = service.load(file, 32);
        assertSame(first, service.load(file, 32));
        verify(provider, times(1)).load(file, 32);
        assertEquals(64, service.load(file, 64).getWidth());
        FileTime modified = Files.getLastModifiedTime(file);
        Files.setLastModifiedTime(file, FileTime.fromMillis(modified.toMillis() + 2000));
        assertNotSame(first, service.load(file, 32));
        Files.writeString(file, "replacement of a different length");
        assertNotNull(service.load(file, 32));
        verify(provider, times(3)).load(file, 32);
    }

    @Test
    void coalescesConcurrentCallsAndAnInterruptedWaiterDoesNotCancelOwner() throws Exception {
        Path file = Files.writeString(directory.resolve("file.jpg"), "test");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ThumbnailProvider provider = mock(ThumbnailProvider.class);
        when(provider.load(file, 32)).thenAnswer(_ -> {
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return Optional.of(image(100, 50));
        });
        ThumbnailService service = new ThumbnailService(provider);
        try (var executor = Executors.newFixedThreadPool(3)) {
            var owner = executor.submit(() -> service.load(file, 32));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            CountDownLatch waiterStarted = new CountDownLatch(1);
            var waiter = executor.submit(() -> {
                waiterStarted.countDown();
                return service.load(file, 32);
            });
            assertTrue(waiterStarted.await(5, TimeUnit.SECONDS));
            waiter.cancel(true);
            var survivor = executor.submit(() -> service.load(file, 32));
            release.countDown();
            assertSame(owner.get(5, TimeUnit.SECONDS), survivor.get(5, TimeUnit.SECONDS));
            verify(provider, times(1)).load(file, 32);
        } finally {
            release.countDown();
        }
    }

    @Test
    void doesNotRetainEmptyResultsFailuresOrImagesOfFilesChangedDuringExtraction() throws Exception {
        Path file = Files.writeString(directory.resolve("file.jpg"), "test");
        ThumbnailProvider provider = mock(ThumbnailProvider.class);
        when(provider.load(file, 32)).thenReturn(Optional.empty())
                .thenThrow(new IOException("unreadable"))
                .thenAnswer(_ -> {
                    Files.writeString(file, "changed during extraction");
                    return Optional.of(image(64, 32));
                }).thenReturn(Optional.of(image(64, 32)));
        ThumbnailService service = new ThumbnailService(provider);
        assertNull(service.load(file, 32));
        assertNull(service.load(file, 32));
        assertNull(service.load(file, 32));
        assertNotNull(service.load(file, 32));
        verify(provider, times(4)).load(file, 32);
    }

    @Test
    void evictsLeastRecentlyUsedEntries() throws Exception {
        ThumbnailProvider provider = mock(ThumbnailProvider.class);
        when(provider.load(any(), eq(16))).thenAnswer(_ -> Optional.of(image(16, 8)));
        ThumbnailService service = new ThumbnailService(provider);
        Path first = Files.writeString(directory.resolve("first.jpg"), "test");
        service.load(first, 16);
        for (int i = 0; i < 256; i++) {
            service.load(Files.writeString(directory.resolve(i + ".jpg"), "test"), 16);
        }
        service.load(first, 16);
        verify(provider, times(2)).load(first, 16);
    }

    private static BufferedImage image(int width, int height) {
        return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    }
}

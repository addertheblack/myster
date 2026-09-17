package com.myster.thumbnail;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import javax.swing.SwingUtilities;

import com.general.thread.PromiseFuture;
import com.general.thread.PromiseFutures;

/**
 * OS thumbnails with a shared, file-state-aware memory cache. Returned images are shared;
 * callers must treat them as read-only. No application disk cache is created.
 */
public final class Thumbnails {
    private Thumbnails() {}

    /**
     * Returns a thumbnail fitting within {@code size} by {@code size} pixels, preserving
     * aspect ratio without cropping, padding or upscaling. Unsupported, missing or unreadable
     * files produce null. Filesystem checks and OS extraction run on thumbnail workers.
     *
     * @param path local file (relative paths resolve against the working directory)
     * @param size maximum dimension, from 1 through 1024 pixels
     * @return shared image, or null when no thumbnail is available
     * @throws IllegalArgumentException if size is outside the supported range
     * @throws IllegalStateException if called on the Swing EDT
     * @throws InterruptedException if the waiting caller is interrupted
     */
    public static BufferedImage summonThumbnail(Path path, int size) throws InterruptedException {
        validate(path, size);
        if (SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Use summonThumbnailAsync on the Swing EDT");
        }
        try {
            return summonThumbnailAsync(path, size).get();
        } catch (ExecutionException e) {
            switch (e.getCause()) {
                case InterruptedException failure -> throw failure;
                case RuntimeException failure -> throw failure;
                case Error failure -> throw failure;
                default -> throw new IllegalStateException("Unexpected thumbnail task failure", e);
            }
        }
    }

    /**
     * Asynchronous version of {@link #summonThumbnail(Path, int)}, safe to call from the EDT.
     * Use {@code .useEdt()} before registering Swing listeners. Cancelling makes this caller's
     * result moot and skips queued work; extraction already started may finish and populate
     * the cache for other callers. Cancellation never cancels another caller's request.
     */
    public static PromiseFuture<BufferedImage> summonThumbnailAsync(Path path, int size) {
        validate(path, size);
        return PromiseFutures.execute(() -> Shared.service.load(path, size), Shared.workers);
    }

    private static void validate(Path path, int size) {
        Objects.requireNonNull(path, "path");
        if (size < 1 || size > 1024) {
            throw new IllegalArgumentException("Thumbnail size must be between 1 and 1024 pixels");
        }
    }

    private static ThumbnailProvider platformProvider() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.startsWith("windows")) {
            return new WindowsThumbnailProvider();
        }
        if (os.startsWith("mac")) {
            return new MacThumbnailProvider();
        }
        if (os.startsWith("linux")) {
            return new LinuxThumbnailProvider();
        }
        return (file, size) -> {
            Logger.getLogger(Thumbnails.class.getName()).fine("No thumbnail provider for " + os);
            return Optional.empty();
        };
    }

    private static final class Shared {
        // Windows COM initialization, extraction and cleanup must stay on the same native thread.
        // Virtual threads are pinned during each native/FFM call but can change carrier threads
        // between calls. BoundedExecutor limits concurrency; it cannot guarantee native-thread
        // identity. Keep platform workers unless Windows acquisition is moved to its own
        // platform-thread executor.
        private static final ExecutorService workers = Executors.newFixedThreadPool(4,
                Thread.ofPlatform().daemon().name("thumbnail-", 0).factory());
        private static final ThumbnailService service = new ThumbnailService(platformProvider());
    }
}

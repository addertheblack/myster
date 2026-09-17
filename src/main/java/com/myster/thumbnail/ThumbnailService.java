package com.myster.thumbnail;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Enforces the platform whitelist, shares acquisition and bounds pixels independently of UI lifetimes. */
final class ThumbnailService {
    private static final Logger log = Logger.getLogger(ThumbnailService.class.getName());
    private static final long MAX_CACHE_BYTES = 16 * 1024 * 1024;
    private static final int MAX_CACHE_ENTRIES = 256;

    private final ThumbnailProvider provider;
    private final ThumbnailPlatform platform;
    private final Map<Key, BufferedImage> cache = new LinkedHashMap<>(32, 0.75f, true);
    private final Map<Key, FutureTask<BufferedImage>> inFlight = new ConcurrentHashMap<>();
    private long cacheBytes;

    ThumbnailService(ThumbnailProvider provider, ThumbnailPlatform platform) {
        this.provider = provider;
        this.platform = platform;
    }

    BufferedImage load(Path path, int size) throws InterruptedException {
        long started = System.nanoTime();
        try {
            if (!platform.isAllowed(path)) {
                log.info(() -> "Thumbnail skipped (extension not allowed on " + platform + "): " + path);
                return null;
            }
            Path file = path.toAbsolutePath().normalize();
            BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
            if (!attributes.isRegularFile()) {
                log.info(() -> "Thumbnail unavailable (not a regular file): " + file);
                return null;
            }
            Key key = Key.of(file, size, attributes);
            synchronized (cache) {
                BufferedImage cached = cache.get(key);
                if (cached != null) {
                    log.info(() -> "Thumbnail source=memory size=" + size + " file=" + file);
                    return cached;
                }
            }
            FutureTask<BufferedImage> task = new FutureTask<>(() -> acquire(key));
            FutureTask<BufferedImage> existing = inFlight.putIfAbsent(key, task);
            if (existing != null) {
                log.info(() -> "Thumbnail source=shared request size=" + size + " file=" + file);
                return await(existing);
            }
            try {
                task.run();
                return await(task);
            } finally {
                inFlight.remove(key, task);
            }
        } catch (IOException e) {
            log.log(Level.INFO, "Thumbnail unavailable for " + path + ": " + e.getMessage());
            log.log(Level.FINE, "Thumbnail acquisition failure", e);
            return null;
        } finally {
            log.info(() -> "Thumbnail request size=" + size + " elapsed="
                    + (System.nanoTime() - started) / 1_000_000 + " ms file=" + path);
        }
    }

    private BufferedImage acquire(Key key) throws IOException, InterruptedException {
        synchronized (cache) {
            BufferedImage cached = cache.get(key);
            if (cached != null) {
                log.info(() -> "Thumbnail source=memory size=" + key.size() + " file=" + key.file());
                return cached;
            }
        }
        Optional<BufferedImage> result = provider.load(key.file(), key.size());
        if (result.isEmpty()) {
            log.info(() -> "Thumbnail source=none size=" + key.size() + " file=" + key.file());
            return null;
        }
        BufferedImage image = ThumbnailImageUtils.fit(result.orElseThrow(), key.size());
        Key after = Key.of(key.file(), key.size(),
                           Files.readAttributes(key.file(), BasicFileAttributes.class));
        if (!key.equals(after)) {
            log.info(() -> "Thumbnail discarded (file changed while loading): " + key.file());
            return null;
        }
        synchronized (cache) {
            cache.put(key, image);
            cacheBytes += bytes(image);
            var entries = cache.entrySet().iterator();
            while (cacheBytes > MAX_CACHE_BYTES || cache.size() > MAX_CACHE_ENTRIES) {
                cacheBytes -= bytes(entries.next().getValue());
                entries.remove();
            }
        }
        return image;
    }

    private static BufferedImage await(FutureTask<BufferedImage> task)
            throws IOException, InterruptedException {
        try {
            return task.get();
        } catch (ExecutionException e) {
            switch (e.getCause()) {
                case IOException failure -> throw failure;
                case InterruptedException failure -> throw failure;
                case RuntimeException failure -> throw failure;
                case Error failure -> throw failure;
                default -> throw new IllegalStateException("Unexpected thumbnail provider failure", e);
            }
        }
    }

    private static long bytes(BufferedImage image) {
        return (long) image.getWidth() * image.getHeight() * 4;
    }

    private record Key(Path file, int size, FileTime modified, FileTime created,
                       long length, Object identity) {
        static Key of(Path file, int size, BasicFileAttributes attributes) {
            return new Key(file, size, attributes.lastModifiedTime(), attributes.creationTime(),
                           attributes.size(), attributes.fileKey());
        }
    }
}

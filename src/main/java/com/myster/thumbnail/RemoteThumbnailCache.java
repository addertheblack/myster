package com.myster.thumbnail;

import java.awt.image.BufferedImage;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import com.general.thread.PromiseFuture;
import com.general.thread.PromiseFutures;
import com.myster.net.MysterAddress;
import com.myster.net.client.MysterStream;
import com.myster.net.stream.ThumbnailProtocolUtils;
import com.myster.net.stream.client.UnknownProtocolException;
import com.myster.type.MysterType;

/**
 * Connection-scoped cache and current request for one preview. All methods and image
 * callbacks belong to the EDT. Replacing, resetting or closing cancels the current promise;
 * {@link RemoteThumbnailTask} serializes this cache's transfers using a shared monitor.
 * Each client window owns one cache; transfers in other windows proceed independently.
 */
public final class RemoteThumbnailCache implements AutoCloseable {
    /** The filename is an opaque remote file reference; size is in device pixels. */
    public record Request(MysterAddress address, MysterType type, String filename, int size) {
        public Request {
            Objects.requireNonNull(address);
            Objects.requireNonNull(type);
            Objects.requireNonNull(filename);
            if (size < 1 || size > ThumbnailProtocolUtils.MAX_SIZE) {
                throw new IllegalArgumentException("Thumbnail size must be between 1 and 256");
            }
        }

        public boolean sameFile(Request other) {
            return address.equals(other.address) && type.equals(other.type)
                    && filename.equals(other.filename);
        }
    }

    /** A null image suppresses retries until expiresAt, in epoch milliseconds. */
    private record Cached(BufferedImage image, long expiresAt) {}

    private static final int MAX_CACHE_ENTRIES = 128;
    private static final long MAX_CACHE_BYTES = 8L * 1024 * 1024;
    private static final long MISS_TTL = 30_000;
    private static final long FAILURE_TTL = 5_000;

    private final MysterStream stream;
    private final Object transferLock = new Object();
    private final Map<Request, Cached> cache = new LinkedHashMap<>(16, .75f, true);
    private final Set<MysterAddress> unsupported = new LinkedHashSet<>();
    private final Consumer<BufferedImage> onLoaded;
    private Optional<Request> currentRequest = Optional.empty();
    private PromiseFuture<BufferedImage> future;
    private boolean closed;

    public RemoteThumbnailCache(MysterStream stream, Consumer<BufferedImage> onLoaded) {
        this.stream = Objects.requireNonNull(stream);
        this.onLoaded = Objects.requireNonNull(onLoaded);
    }

    /**
     * Replaces the preview request, or withdraws it when empty. An identical pending request
     * is reused. Only available images are published; the caller clears old-file pixels.
     * Misses and failures retry on a later call after their suppression interval expires.
     */
    public void replace(Optional<Request> request) {
        if (closed || (request.equals(currentRequest) && future != null)) {
            return;
        }
        if (future != null) {
            future.cancel();
            future = null;
        }
        currentRequest = request;
        request.ifPresent(this::load);
    }

    private void load(Request request) {
        Optional<BufferedImage> image = lookup(request);
        if (image.isPresent()) {
            onLoaded.accept(image.get());
            return;
        }
        Cached previous = cache.get(request);
        if (unsupported.contains(request.address())
                || (previous != null && previous.expiresAt > System.currentTimeMillis())) {
            return;
        }
        PromiseFuture<BufferedImage> next = PromiseFutures.execute(new RemoteThumbnailTask(stream,
                transferLock, request.address(), request.type(), request.filename(), request.size()))
                .useEdt();
        future = next;
        next.addResultListener(result -> {
                    putCache(request, new Cached(result, result == null
                            ? System.currentTimeMillis() + MISS_TTL : Long.MAX_VALUE));
                    if (result != null) {
                        onLoaded.accept(result);
                    }
                })
                .addExceptionListener(error -> {
                    if (error instanceof UnknownProtocolException) {
                        unsupported.add(request.address());
                        if (unsupported.size() > MAX_CACHE_ENTRIES) {
                            unsupported.remove(unsupported.iterator().next());
                        }
                    } else {
                        putCache(request, new Cached(null, System.currentTimeMillis() + FAILURE_TTL));
                    }
                })
                .addFinallyListener(() -> {
                    if (future == next) {
                        future = null;
                    }
                });
    }

    /** Returns the exact or smallest larger cached request for this file. */
    private Optional<BufferedImage> lookup(Request request) {
        return cache.entrySet().stream()
                .filter(entry -> entry.getValue().image != null
                        && entry.getKey().sameFile(request)
                        && entry.getKey().size() >= request.size())
                .map(Map.Entry::getKey)
                .min(Comparator.comparingInt(Request::size))
                .map(key -> cache.get(key).image);
    }

    private void putCache(Request request, Cached value) {
        cache.put(request, value);
        long bytes = cache.values().stream().mapToLong(cached -> bytes(cached.image)).sum();
        var entries = cache.values().iterator();
        while (cache.size() > MAX_CACHE_ENTRIES || bytes > MAX_CACHE_BYTES) {
            bytes -= bytes(entries.next().image);
            entries.remove();
        }
    }

    private static long bytes(BufferedImage image) {
        return image == null ? 0 : (long) image.getWidth() * image.getHeight() * 4;
    }

    /** Withdraws the request and forgets all connection-scoped images and outcomes. */
    public void reset() {
        replace(Optional.empty());
        cache.clear();
        unsupported.clear();
    }

    /** Terminal reset. Later requests are ignored; socket cleanup stays on its worker. */
    @Override
    public void close() {
        reset();
        closed = true;
    }
}

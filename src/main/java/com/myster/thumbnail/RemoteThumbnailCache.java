package com.myster.thumbnail;

import java.awt.image.BufferedImage;
import java.security.PublicKey;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.general.thread.Invoker;
import com.general.thread.PromiseFuture;
import com.general.thread.PromiseFutures;
import com.myster.net.MysterAddress;
import com.myster.net.client.MysterStream;
import com.myster.net.stream.ThumbnailProtocolUtils;
import com.myster.net.stream.client.UnknownProtocolException;
import com.myster.type.MysterType;

/**
 * Owner-scoped cache shared by independent thumbnail consumers, including multi-server search
 * tabs. Public state methods
 * belong to the EDT. Each {@link #load(Request)} call owns its promise; resetting or closing
 * cancels all outstanding promises;
 * {@link RemoteThumbnailTask} serializes this cache's transfers using a shared monitor.
 * Each client window or search tab owns one cache; other owners proceed independently.
 * Initially address-only requests are resolved by the stream on the transfer's own socket.
 * Published pixels and unsupported-peer outcomes use the verified key; the bounded resolution
 * map pins subsequent requests until reset. Pending address-only failures contain no pixels.
 */
public final class RemoteThumbnailCache implements AutoCloseable {
    /**
     * One identity-scoped thumbnail request.
     * @param address server endpoint
     * @param type network type containing the file
     * @param filename opaque remote file reference
     * @param size requested maximum side in device pixels, from 1 through 256
     * @param expectedKey server key to verify; empty only until the protocol discovers identity
     */
    public record Request(MysterAddress address, MysterType type, String filename, int size,
                          Optional<PublicKey> expectedKey) {
        public Request(MysterAddress address, MysterType type, String filename, int size, PublicKey key) {
            this(address, type, filename, size, Optional.of(Objects.requireNonNull(key)));
        }

        public Request {
            Objects.requireNonNull(address);
            Objects.requireNonNull(type);
            Objects.requireNonNull(filename);
            Objects.requireNonNull(expectedKey);

            if (size < 1 || size > ThumbnailProtocolUtils.MAX_SIZE) {
                throw new IllegalArgumentException("Thumbnail size must be between 1 and 256");
            }
        }

        public boolean sameFile(Request other) {
            return address.equals(other.address) && type.equals(other.type)
                    && filename.equals(other.filename) && expectedKey.equals(other.expectedKey);
        }

        public Request withKey(PublicKey key) {
            return new Request(address, type, filename, size, key);
        }
    }

    /** A null image suppresses retries until expiresAt, in epoch milliseconds. */
    private record Cached(BufferedImage image, long expiresAt) {}
    private record Endpoint(MysterAddress address, Optional<PublicKey> expectedKey) {}

    private static final int MAX_CACHE_ENTRIES = 512;
    private static final long MAX_CACHE_BYTES = 8L * 1024 * 1024;
    private static final long MISS_TTL = 30_000;
    private static final long FAILURE_TTL = 5_000;

    private final MysterStream stream;
    private final Object transferLock = new Object();
    private final Map<Request, Cached> cache = new LinkedHashMap<>(16, .75f, true);
    private final Set<Endpoint> unsupported = new LinkedHashSet<>();
    private final Set<PromiseFuture<BufferedImage>> outstanding = new LinkedHashSet<>();
    private final Map<MysterAddress, PublicKey> resolvedKeys = new LinkedHashMap<>();

    private boolean closed;
    private long generation;

    public RemoteThumbnailCache(MysterStream stream) {
        this.stream = Objects.requireNonNull(stream);
    }

    /**
     * Starts one request unless a usable cached result, suppression outcome, or unsupported
     * endpoint already exists.
     *
     * @return the owned future; after close it is already cancelled
     */
    public PromiseFuture<BufferedImage> load(Request request) {
        Objects.requireNonNull(request);
        return loadResolved(resolve(request));
    }

    /** Binds an initially address-only target to this cache generation's first verified identity. */
    public Request resolve(Request request) {
        PublicKey key = resolvedKeys.get(request.address());
        return request.expectedKey().isEmpty() && key != null ? request.withKey(key) : request;
    }

    private PromiseFuture<BufferedImage> loadResolved(Request request) {
        if (closed) {
            PromiseFuture<BufferedImage> cancelled = PromiseFuture.newPromiseFuture((BufferedImage) null);
            cancelled.cancel();
            return cancelled;
        }
        Optional<BufferedImage> image = lookup(request);
        if (image.isPresent()) {
            return PromiseFuture.newPromiseFuture(image.get());
        }
        Cached previous = cache.get(request);
        if (unsupported.contains(new Endpoint(request.address(), request.expectedKey()))
                || (previous != null && previous.expiresAt > System.currentTimeMillis())) {
            return PromiseFuture.newPromiseFuture((BufferedImage) null);
        }
        long token = generation;
        RemoteThumbnailTask task = new RemoteThumbnailTask(stream, transferLock, request.address(),
                request.type(), request.filename(), request.size(), request.expectedKey());
        PromiseFuture<BufferedImage> next = PromiseFutures.execute(task);
        outstanding.add(next);
        next.withInvoker(Invoker.EDT).addResultListener(result -> {
                if (closed || generation != token) {
                    return;
                }
                Request verified = verifiedRequest(request, task);
                putCache(verified, new Cached(result, result == null
                        ? System.currentTimeMillis() + MISS_TTL : Long.MAX_VALUE));
            })
            .addExceptionListener(error -> {
                if (closed || generation != token) {
                    return;
                }
                Request verified = verifiedRequest(request, task);
                if (error instanceof UnknownProtocolException) {
                    unsupported.add(new Endpoint(verified.address(), verified.expectedKey()));
                    if (unsupported.size() > MAX_CACHE_ENTRIES) {
                        unsupported.remove(unsupported.iterator().next());
                    }
                } else {
                    putCache(verified, new Cached(null, System.currentTimeMillis() + FAILURE_TTL));
                }
            })
            .addFinallyListener(() -> {
                outstanding.remove(next);
            });
        return next;
    }

    /** Returns the exact or smallest larger cached request for this file without starting work. */
    public Optional<BufferedImage> lookup(Request request) {
        Request target = resolve(request);
        return cache.entrySet().stream()
                .filter(entry -> entry.getValue().image != null
                        && entry.getKey().sameFile(target)
                        && entry.getKey().size() >= target.size())
                .map(Map.Entry::getKey)
                .min(Comparator.comparingInt(Request::size))
                .map(key -> cache.get(key).image);
    }

    private Request verifiedRequest(Request request, RemoteThumbnailTask task) {
        PublicKey key = task.verifiedKey();
        if (key == null) {
            return request;
        }
        resolvedKeys.putIfAbsent(request.address(), key);
        while (resolvedKeys.size() > MAX_CACHE_ENTRIES) {
            resolvedKeys.remove(resolvedKeys.keySet().iterator().next());
        }
        return request.withKey(key);
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
        generation++;
        for (PromiseFuture<BufferedImage> promise : Set.copyOf(outstanding)) {
            promise.cancel();
        }
        outstanding.clear();
        cache.clear();
        unsupported.clear();
        resolvedKeys.clear();
    }

    /** Terminal reset. Later requests are ignored; socket cleanup stays on its worker. */
    @Override
    public void close() {
        reset();
        closed = true;
    }
}

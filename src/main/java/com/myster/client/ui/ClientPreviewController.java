package com.myster.client.ui;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import com.general.thread.PromiseFuture;
import com.general.thread.PromiseFutures;
import com.myster.thumbnail.RemoteThumbnailCache;

/**
 * EDT-owned preview selection and visibility. Geometry changes debounce new requests for
 * 150 ms; hiding or losing the selection cancels immediately. Same-file pixels remain visible
 * during a size upgrade. Closing also closes the owned cache.
 */
public final class ClientPreviewController implements AutoCloseable {
    private final ClientFilePreviewPane pane;
    private final RemoteThumbnailCache thumbnailCache;
    private final Supplier<Optional<RemoteThumbnailCache.Request>> requestSupplier;
    private final BooleanSupplier active;
    private PromiseFuture<Void> resizeDelay;
    private Optional<RemoteThumbnailCache.Request> currentRequest = Optional.empty();
    private boolean closed;

    public ClientPreviewController(ClientFilePreviewPane pane,
                                   RemoteThumbnailCache thumbnailCache,
                                   Supplier<Optional<RemoteThumbnailCache.Request>> requestSupplier,
                                   BooleanSupplier active) {
        this.pane = Objects.requireNonNull(pane);
        this.thumbnailCache = Objects.requireNonNull(thumbnailCache);
        this.requestSupplier = Objects.requireNonNull(requestSupplier);
        this.active = Objects.requireNonNull(active);
        pane.setGeometryListener(this::scheduleResize);
    }

    public void reconcile() {
        if (closed) return;
        cancelResize();
        Optional<RemoteThumbnailCache.Request> next = visibleRequest();
        if (next.isEmpty() || currentRequest.isEmpty()
                || !next.get().sameFile(currentRequest.get())) {
            pane.clearThumbnail();
        }
        currentRequest = next;
        thumbnailCache.replace(next);
    }

    private void scheduleResize() {
        if (closed) return;
        Optional<RemoteThumbnailCache.Request> next = visibleRequest();
        if (next.isEmpty()) {
            reconcile();
            return;
        }
        if (next.equals(currentRequest) && resizeDelay == null) return;
        cancelResize();
        thumbnailCache.replace(Optional.empty());
        resizeDelay = PromiseFutures.delay(Duration.ofMillis(150))
                .useEdt()
                .addResultListener(_ -> reconcile());
    }

    private Optional<RemoteThumbnailCache.Request> visibleRequest() {
        return active.getAsBoolean() ? requestSupplier.get() : Optional.empty();
    }

    private void cancelResize() {
        if (resizeDelay != null) {
            resizeDelay.cancel();
            resizeDelay = null;
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        cancelResize();
        pane.setGeometryListener(() -> {});
        thumbnailCache.close();
        pane.clearThumbnail();
    }
}

package com.myster.client.ui;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.awt.image.BufferedImage;

import com.general.thread.PromiseFuture;
import com.general.thread.PromiseFutures;
import com.general.thread.Invoker;
import com.myster.thumbnail.RemoteThumbnailCache;

/**
 * EDT-owned preview selection and visibility. Geometry changes debounce new requests for
 * 150 ms; hiding or losing the selection cancels immediately. Same-file pixels remain visible
 * during a size upgrade. The shared cache is owned by the client window.
 */
public final class ClientPreviewController implements AutoCloseable {
    private final ClientFilePreviewPane pane;
    private final RemoteThumbnailCache thumbnailCache;
    private final Supplier<Optional<RemoteThumbnailCache.Request>> requestSupplier;
    private final BooleanSupplier active;
    private PromiseFuture<Void> resizeDelay;
    private PromiseFuture<BufferedImage> thumbnailFuture;
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

    public void conditionalReload() {
        if (closed) return;
        cancelResize();
        Optional<RemoteThumbnailCache.Request> next = visibleRequest();
        if (next.isEmpty() || currentRequest.isEmpty()
                || !next.get().sameFile(currentRequest.get())) {
            pane.clearThumbnail();
        }
        boolean sameRequest = next.equals(currentRequest);
        currentRequest = next;
        if (sameRequest && thumbnailFuture != null) {
            return;
        }
        cancelThumbnail();
        next.ifPresent(this::load);
    }

    private void scheduleResize() {
        if (closed) return;
        Optional<RemoteThumbnailCache.Request> next = visibleRequest();
        if (next.isEmpty()) {
            conditionalReload();
            return;
        }
        if (next.equals(currentRequest) && resizeDelay == null) return;
        cancelResize();
        cancelThumbnail();
        resizeDelay = PromiseFutures.delay(Duration.ofMillis(150))
                .useEdt()
                .addResultListener(_ -> conditionalReload());
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

    private void load(RemoteThumbnailCache.Request request) {
        if (thumbnailFuture!=null) {
            thumbnailFuture.cancel();
            thumbnailFuture = null;
        }

        PromiseFuture<BufferedImage> future = thumbnailCache.load(request).withInvoker(Invoker.EDT);
        thumbnailFuture = future;
        future.addResultListener(image -> {
            if (thumbnailFuture != null && currentRequest.filter(request::equals).isPresent()) {
                pane.setThumbnail(image);
            }
        }).addExceptionListener(_ -> {}).addFinallyListener(() -> {
            if (thumbnailFuture == future) {
                thumbnailFuture = null;
            }
        });
    }

    private void cancelThumbnail() {
        if (thumbnailFuture != null) {
            thumbnailFuture.cancel();
            thumbnailFuture = null;
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        cancelResize();
        pane.setGeometryListener(() -> {});
        cancelThumbnail();
        pane.clearThumbnail();
    }
}

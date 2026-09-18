package com.myster.net.stream.server;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Path;
import java.util.Objects;

import com.myster.access.AccessEnforcementUtils;
import com.myster.access.AccessListReader;
import com.myster.filemanager.FileItem;
import com.myster.net.server.ConnectionContext;
import com.myster.net.stream.ThumbnailProtocolUtils;

/**
 * Section 79: one thumbnail request/response, using the wire contract in
 * {@link ThumbnailProtocolUtils}. The inherited handler sends the standard acknowledgement.
 * Denied, unshared, missing and unavailable thumbnails all produce an empty MessagePak.
 * Access is checked using the transport's caller identity before file lookup or acquisition.
 */
public final class ThumbnailStreamServer extends ServerStreamHandler {
    public static final int NUMBER = ThumbnailProtocolUtils.SECTION_NUMBER;

    private final AccessListReader accessListReader;
    private final ThumbnailSource thumbnails;

    public ThumbnailStreamServer(AccessListReader accessListReader, ThumbnailSource thumbnails) {
        this.accessListReader = Objects.requireNonNull(accessListReader, "accessListReader");
        this.thumbnails = Objects.requireNonNull(thumbnails, "thumbnails");
    }

    @Override
    public int getSectionNumber() {
        return NUMBER;
    }

    @Override
    public void section(ConnectionContext context) throws IOException {
        var request = ThumbnailProtocolUtils.readRequest(context.socket().in);
        BufferedImage image = null;
        if (AccessEnforcementUtils.isAllowed(request.type(), context.callerCid(), accessListReader)
                && context.fileManager().isShared(request.type())) {
            FileItem item = context.fileManager().getFileItem(request.type(), request.filename());
            if (item != null) {
                try {
                    image = thumbnails.load(item.getPath(), request.size());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    InterruptedIOException failure = new InterruptedIOException("Thumbnail acquisition interrupted");
                    failure.initCause(exception);
                    throw failure;
                }
            }
        }
        ThumbnailProtocolUtils.writeResponse(context.socket().out, image, request.size());
    }

    /** Blocking local acquisition, wired to the facade that owns the OS thumbnail workers. */
    @FunctionalInterface
    public interface ThumbnailSource {
        /** Returns a shared read-only image fitting the size bound, or null when unavailable. */
        BufferedImage load(Path path, int size) throws InterruptedException;
    }
}

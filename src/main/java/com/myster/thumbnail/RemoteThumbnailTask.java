package com.myster.thumbnail;

import java.awt.image.BufferedImage;
import java.util.Objects;
import java.util.concurrent.CancellationException;

import com.general.thread.AbstractCancellableCallable;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.net.client.MysterStream;
import com.myster.type.MysterType;

/**
 * Serializes remote thumbnail transfers sharing the supplied monitor, including socket cleanup.
 * Cancellation skips work waiting for the monitor or still connecting. A transfer already
 * reading may finish; cancelling its promise discards the result without blocking the EDT.
 */
public final class RemoteThumbnailTask extends AbstractCancellableCallable<BufferedImage> {
    private final MysterStream stream;
    private final Object transferLock;
    private final MysterAddress address;
    private final MysterType type;
    private final String filename;
    private final int size;

    /**
     * @param transferLock shared by all tasks from one cache, including cancelled transfers
     *                     still finishing after a reset; only worker threads acquire it
     */
    public RemoteThumbnailTask(MysterStream stream,
                               Object transferLock,
                               MysterAddress address,
                               MysterType type,
                               String filename,
                               int size) {
        this.stream = Objects.requireNonNull(stream);
        this.transferLock = Objects.requireNonNull(transferLock);
        this.address = Objects.requireNonNull(address);
        this.type = Objects.requireNonNull(type);
        this.filename = Objects.requireNonNull(filename);
        this.size = size;
    }

    @Override
    public BufferedImage call() throws Exception {
        synchronized (transferLock) {
            checkCancelled();
            try (MysterSocket opened = stream.makeStreamConnection(address)) {
                checkCancelled();
                return stream.getThumbnail(opened, type, filename, size);
            }
        }
    }

    private void checkCancelled() {
        if (isCancelled()) {
            throw new CancellationException("Thumbnail request cancelled");
        }
    }
}

package com.myster.thumbnail;

import java.awt.image.BufferedImage;
import java.security.PublicKey;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;

import com.general.thread.AbstractCancellableCallable;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.net.client.MysterStream;
import com.myster.net.client.ParamBuilder;
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
    private final Optional<PublicKey> expectedKey;
    private PublicKey verifiedKey;

    /** Every thumbnail connection must verify this key, even after earlier verification. */
    public RemoteThumbnailTask(MysterStream stream,
                               Object transferLock,
                               MysterAddress address,
                               MysterType type,
                               String filename,
                               int size,
                               PublicKey expectedKey) {
        this(stream, transferLock, address, type, filename, size,
                Optional.of(Objects.requireNonNull(expectedKey)));
    }

    /** Empty expected key asks the stream to discover identity on this transfer's own socket. */
    RemoteThumbnailTask(MysterStream stream, Object transferLock, MysterAddress address,
                         MysterType type, String filename, int size, Optional<PublicKey> expectedKey) {
        this.expectedKey = Objects.requireNonNull(expectedKey);
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

            ParamBuilder params = new ParamBuilder(address);
            if (expectedKey.isPresent()) {
                params = params.withExpectedServerPublicKey(expectedKey.get());
            }
            try (MysterSocket opened = stream.makeStreamConnection(params)) {
                checkCancelled();
                verifiedKey = expectedKey.orElse(null);
                if (verifiedKey == null) {
                    verifiedKey = opened.getAuthenticatedPeerKey()
                            .orElseThrow(() -> new java.io.IOException("Thumbnail peer has no public key"));
                }
                return stream.getThumbnail(opened, type, filename, size);
            }
        }
    }

    /** Valid after the transfer future completes; null means connection establishment failed. */
    PublicKey verifiedKey() {
        return verifiedKey;
    }

    private void checkCancelled() {
        if (isCancelled()) {
            throw new CancellationException("Thumbnail request cancelled");
        }
    }
}

package com.myster.link;

import java.io.IOException;
import java.nio.file.Files;
import java.security.PublicKey;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntSupplier;

import com.general.thread.PromiseFuture;
import com.general.thread.PromiseFutures;
import com.myster.cid.ServerCid;
import com.myster.filemanager.FileItem;
import com.myster.filemanager.FileTypeListManager;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.net.client.MysterProtocol;
import com.myster.net.client.ParamBuilder;
import com.myster.search.MysterFileStub;
import com.myster.threedns.ThreeDnsLookupResult;
import com.myster.tracker.PublicKeyIdentity;
import com.myster.type.MysterType;

/** Resolves resource locations without importing types, changing sharing, or starting downloads. */
public final class MysterLinkService {
    /** Verified server and optional type/file destination; file names remain opaque references. */
    public record Target(PublicKeyIdentity identity, MysterAddress address, Optional<MysterType> type,
                         Optional<String> name) {}

    private final MysterProtocol protocol;
    private final FileTypeListManager files;
    private final PublicKeyIdentity localIdentity;
    private final IntSupplier localPort;

    public MysterLinkService(MysterProtocol protocol,
                             FileTypeListManager files,
                             PublicKeyIdentity localIdentity,
                             IntSupplier localPort) {
        this.protocol = Objects.requireNonNull(protocol);
        this.files = Objects.requireNonNull(files);
        this.localIdentity = Objects.requireNonNull(localIdentity);
        this.localPort = Objects.requireNonNull(localPort);
    }

    public boolean isLocal(MysterLinkUri uri) {
        return uri.server().equals(ServerCid.fromPublicKey(localIdentity.getPublicKey()));
    }

    /** Local callers must obtain affirmative own-server confirmation before invoking this method. */
    public PromiseFuture<Target> prepare(MysterLinkUri uri) {
        if (isLocal(uri)) {
            return PromiseFutures.execute(() -> prepareLocal(uri));
        }

        return protocol.getDnsLookup().resolve(uri.server()).mapAsyncInline(result -> {
            if (result.status() != ThreeDnsLookupResult.Status.EXACT_VERIFIED) {
                return PromiseFuture.newPromiseFutureException(new IOException("The exact server could not be found: " + result.status()));
            }

            var peer = result.exactPeer().orElseThrow();
            if (!peer.cid().equals(uri.server())) {
                return PromiseFuture.newPromiseFutureException(new IOException("The resolved server CID does not match."));
            }

            return PromiseFuture.newPromiseFuture(new Target(peer.identity(), peer.address(), uri.type(), uri.name()));
        });
    }

    private Target prepareLocal(MysterLinkUri uri) throws IOException {
        MysterAddress address = MysterAddress.createMysterAddress("127.0.0.1:" + localPort.getAsInt());
        if (uri.name().isPresent()) {
            localFile(uri.type().orElseThrow(), uri.name().get());
        }

        return new Target(localIdentity, address, uri.type(), uri.name());
    }

    private FileItem localFile(MysterType type, String name) throws IOException {
        if (!files.isShared(type)) {
            throw new IOException("This type is not currently shared.");
        }

        FileItem item = files.getFileItem(type, name);
        if (item == null || !Files.isRegularFile(item.getPath())) {
            throw new IOException("The shared file is no longer available.");
        }

        return item;
    }

    /** Builds a name-based link from the saved, shared index. */
    public MysterLinkUri createLocal(MysterType type, String name) throws IOException {
        FileItem item = localFile(type, name);

        return MysterLinkUri.create(ServerCid.fromPublicKey(localIdentity.getPublicKey()),
                                    Optional.of(type),
                                    Optional.of(item.getName()));
    }

    /** Authenticates the endpoint to obtain its CID; copying requires no file metadata. */
    public PromiseFuture<MysterLinkUri> createRemote(MysterFileStub stub, Optional<PublicKey> expectedKey) {
        return PromiseFutures.execute(() -> {
            ParamBuilder params = new ParamBuilder(stub.ip());
            if (expectedKey.isPresent()) {
                params = params.withExpectedServerPublicKey(expectedKey.get());
            }

            try (MysterSocket socket = protocol.getStream().makeStreamConnection(params)) {
                PublicKey key = socket.getAuthenticatedPeerKey()
                                      .orElseThrow(() -> new IOException(
                                              "The server has no authenticated public-key identity."));
                return MysterLinkUri.create(ServerCid.fromPublicKey(key),
                                            Optional.of(stub.type()),
                                            Optional.of(stub.name()));
            }
        });
    }
}

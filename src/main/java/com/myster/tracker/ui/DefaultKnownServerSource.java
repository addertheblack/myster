package com.myster.tracker.ui;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import com.general.thread.PromiseFuture;
import com.myster.cid.ServerCid;
import com.myster.net.MysterAddress;
import com.myster.net.client.DnsLookupProtocol;
import com.myster.threedns.ThreeDnsLookupResult;
import com.myster.tracker.MysterServer;
import com.myster.tracker.MysterServerPool;
import com.myster.tracker.PublicKeyIdentity;

/** Adapts the shared server pool and 3DNS resolver for server-picker callers. */
public final class DefaultKnownServerSource implements KnownServerSource {
    private final MysterServerPool pool;
    private final DnsLookupProtocol dnsLookup;

    public DefaultKnownServerSource(MysterServerPool pool, DnsLookupProtocol dnsLookup) {
        this.pool = Objects.requireNonNull(pool, "pool");
        this.dnsLookup = Objects.requireNonNull(dnsLookup, "dnsLookup");
    }

    @Override
    public void forEachServer(Consumer<MysterServer> consumer) {
        pool.forEach(consumer);
    }

    @Override
    public Optional<String> resolveDisplayName(ServerCid cid) {
        return pool.lookupIdentityFromCid(cid)
                .map(PublicKeyIdentity::new)
                .flatMap(pool::getCachedMysterServer)
                .map(MysterServer::getServerName)
                .filter(name -> !name.isBlank());
    }

    @Override
    public PromiseFuture<MysterServer> resolveServer(MysterAddress address) {
        return pool.resolveServer(address);
    }

    @Override
    public PromiseFuture<MysterServer> resolveServerCid(ServerCid cid) {
        return dnsLookup.resolve(cid).mapAsyncInline(result -> {
            if (result.status() != ThreeDnsLookupResult.Status.EXACT_VERIFIED) {
                return PromiseFuture.newPromiseFutureException(
                        new IOException("3DNS could not find the exact server: " + result.status()));
            }

            var peer = result.exactPeer().orElseThrow();
            if (!peer.cid().equals(cid)) {
                return PromiseFuture.newPromiseFutureException(
                        new IOException("The resolved server CID does not match."));
            }

            return pool.resolveServer(peer.address()).mapAsyncInline(server -> {
                if (!peer.identity().equals(server.getIdentity())) {
                    return PromiseFuture.newPromiseFutureException(
                            new IOException("The responding server identity does not match the resolved CID."));
                }
                return PromiseFuture.newPromiseFuture(server);
            });
        });
    }
}

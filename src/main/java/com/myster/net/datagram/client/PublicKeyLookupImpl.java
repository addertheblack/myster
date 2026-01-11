package com.myster.net.datagram.client;

import static com.myster.tracker.MysterIdentity.extractPublicKey;

import java.security.PublicKey;
import java.util.Optional;

import com.general.thread.PromiseFuture;
import com.myster.net.MysterAddress;
import com.myster.tracker.MysterIdentity;
import com.myster.tracker.MysterServerPool;

public class PublicKeyLookupImpl implements PublicKeyLookup {
    private volatile MysterServerPool pool;

    public void setMysterServerPool(MysterServerPool pool) {
        this.pool = pool;
    }
    
    @Override
    public Optional<PublicKey> convert(MysterIdentity identity) {
        return pool.getCachedMysterServer(identity)
                .map(s -> s.getIdentity())
                .flatMap(i -> extractPublicKey(i));
    }

    @Override
    public Optional<PublicKey> getCached(MysterAddress address) {
        return pool.getCachedMysterServer(address)
                .map(s -> s.getIdentity())
                .flatMap(i -> extractPublicKey(i));
    }

    @Override
    public PromiseFuture<Optional<PublicKey>> fetchPublicKey(MysterAddress address) {
        throw new IllegalStateException("Not yet implemented. Is a bit stupid quite frankly.");
    }

}

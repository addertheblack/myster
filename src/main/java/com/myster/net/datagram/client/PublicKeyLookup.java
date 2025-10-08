package com.myster.net.datagram.client;

import java.security.PublicKey;
import java.util.Optional;

import com.general.thread.PromiseFuture;
import com.myster.net.MysterAddress;
import com.myster.tracker.MysterIdentity;

/**
 * Used to lookup the server PublicKey given an Identity, MysterAddress
 */
public interface PublicKeyLookup {
    /**
     * @param identity to convert to a public key
     * @return the public key for this identity or empty if not found
     */
    Optional<PublicKey> convert(MysterIdentity identity);
    Optional<PublicKey> getCached(MysterAddress address);
    PromiseFuture<Optional<PublicKey>> fetchPublicKey(MysterAddress address);
}
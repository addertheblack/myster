package com.myster.net.datagram.client;

import java.security.PublicKey;
import java.util.Optional;

import com.general.thread.PromiseFuture;
import com.myster.identity.Identity;
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
    
    /**
     * Get the server's public key for a given address (synchronous version)
     */
    default Optional<PublicKey> getServerPublicKey(MysterAddress address) {
        Optional<PublicKey> cached = getCached(address);
        if (cached.isPresent()) {
            return cached;
        }
        // For now, return empty - async version would be used in production
        return Optional.empty();
    }
    
    /**
     * Get the default client identity for signing packets
     */
    default Optional<Identity> getDefaultClientIdentity() {
        return Optional.empty(); // TODO: Implement when identity system is integrated
    }
}
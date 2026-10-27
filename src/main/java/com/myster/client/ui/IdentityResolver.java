package com.myster.client.ui;

import java.util.Optional;

import com.myster.net.MysterAddress;
import com.myster.tracker.PublicKeyIdentity;

/**
 * Non-blocking lookup of public-key identities already known to the server pool.
 * An empty result requires asynchronous server discovery before opening a ClientWindow.
 */
public interface IdentityResolver {
    /**
     * Returns a cached public-key identity without DNS or network I/O.
     *
     * @param address the address to resolve
     * @return the identity if known, empty otherwise
     */
    Optional<PublicKeyIdentity> getCached(MysterAddress address);
}

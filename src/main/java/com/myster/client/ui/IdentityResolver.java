package com.myster.client.ui;

import java.util.Optional;

import com.myster.net.MysterAddress;
import com.myster.tracker.MysterIdentity;

/**
 * Interface to resolve a MysterAddress to a MysterIdentity.
 * This allows the ClientWindowProvider to look up server identities without
 * depending on the entire MysterServerPool interface.
 */
public interface IdentityResolver {
    /**
     * Resolves a MysterAddress to its corresponding MysterIdentity.
     *
     * @param address the address to resolve
     * @return the identity if known, empty otherwise
     */
    Optional<MysterIdentity> resolve(MysterAddress address);
}


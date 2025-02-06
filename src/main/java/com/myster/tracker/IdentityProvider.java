
package com.myster.tracker;

import java.util.Optional;

import com.myster.net.MysterAddress;

/**
 * Exists to allow {@link MysterServerImplementation} access to {@link IdentityTracker} related information without
 * giving access to the {@link IdentityTracker} which also contains write/modify methods
 */
public interface IdentityProvider {
    /**
     * @param address
     *            to check
     * @return true if we know what server identity is related to this address
     */
    boolean exists(MysterAddress address);

    boolean existsMysterIdentity(MysterIdentity identity);

    /**
     * @param address
     *            to lookup
     * @return the identity of the server with this address
     */
    MysterIdentity getIdentity(MysterAddress address);

    MysterIdentity getIdentityFromExternalName(ExternalName name);

    /**
     * @param identity
     *            server to get address for
     * @return the address we should use to contact this server.
     *         Optional.empty() will be returned if the server is down
     */
    Optional<MysterAddress> getBestAddress(MysterIdentity identity);

    /**
     * @param identity
     *            to lookup
     * @return all addresses known to be associated with this server
     */
    MysterAddress[] getAddresses(MysterIdentity identity);

    /**
     * @param address
     *            to lookup
     * @return true if server was "up" last time it was pinged or false if the
     *         address is not known or was down last time it was pinged.
     */
    boolean isUp(MysterAddress address);

    int getPing(MysterAddress address);


    void removeIdentity(MysterIdentity key, MysterAddress address);

    void addIdentity(MysterIdentity identity, MysterAddress address);
}

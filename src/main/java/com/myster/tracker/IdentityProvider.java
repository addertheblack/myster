
package com.myster.tracker;

import java.util.Optional;

import com.myster.net.MysterAddress;

public interface IdentityProvider {
    /**
     * @param address to check
     * @return true if we know what server identity is related to this address
     */
    public boolean exists(MysterAddress address);
    
    /**
     * @param address to lookup
     * @return the identity of the server with this address
     */
    public MysterIdentity getIdentity(MysterAddress address);

    /**
     * @param identity
     *            server to get address for
     * @return the address we should use to contact this server.
     *         Optional.empty() will be returned if the server is down
     */
    public Optional<MysterAddress> getBestAddress(MysterIdentity identity);
    
    /**
     * @param identity to lookup
     * @return all addresses known to be associated with this server
     */
    public MysterAddress[] getAddresses(MysterIdentity identity);
    
    /**
     * @param address
     *            to lookup
     * @return true if server was "up" last time it was pinged or false if the
     *         address is not known or was down last time it was pinged.
     */
    public boolean isUp(MysterAddress address);
}

/* 

 Title:			Myster Open Source
 Author:			Andrew Trumper
 Description:	Generic Myster Code
 
 This code is under GPL

 Copyright Andrew Trumper 2000-2001
 */

package com.myster.tracker;

import java.security.PublicKey;
import java.util.Optional;
import java.util.function.Consumer;

import com.myster.identity.Cid128;
import com.myster.net.MysterAddress;

/**
 * This class exists to make sure that if a server is listed under many
 * categories (ie it's a good MPG3 server as well as being just excellent in
 * ROMS) that no additional Memory is wasted listing the server TWICE.. It also
 * cuts down on the number of pings a very good server receives.
 */
public interface MysterServerPool {
    /**
     * ONLY UNIT TESTS!
     */
    void suggestAddress(String address);

    Optional<MysterIdentity> lookupIdentityFromName(ExternalName externalName);

    Optional<PublicKey> lookupIdentityFromCid(Cid128 cid);

    /**
     * @return The MysterServer for this address assuming it's already in the cache. Empty Optional otherwise.
     */
    Optional<MysterServer> getCachedMysterServer(MysterIdentity identity);

    Optional<MysterServer> getCachedMysterServer(MysterAddress address);

    /**
     * @return true if the MysterServer is in the cache, false otherwise. Danger of race condition
     * between the check and the call to {@link MysterServerPool#getCachedMysterServer(MysterAddress)}
     */
    boolean existsInPool(MysterIdentity identity);

    /**
     * @return true if the MysterServer is in the cache, false otherwise. Danger of race condition
     * between the check and the call to {@link MysterServerPool#getCachedMysterServer(MysterAddress)}
     */
    boolean existsInPool(MysterAddress address);

    /**
     * When a new MysterServer is discovered the MysterServerPool is so excited it
     * has to tell anyone who will listen
     *
     * @param listener when a new server has just been discovered
     */
    void addPoolListener(MysterPoolListener listener);

    void removePoolListener(MysterPoolListener listener);

    /**
     * Only useful when {@link MysterTypeServerList} are loading. There's a brief time when the {@link MysterServerPool}
     * has been loaded but no MysterTypeServerList have been loaded. In order to stop everything from being GCed we
     * keep a hard link to EVERYTHING initially. Call this when MysterTypeServerLists are done loading.
     */
    void clearHardLinks();

    /**
     * Iterate through all known servers
     */
    void forEach(Consumer<MysterServer> consumer);

    /**
     * Call this method if we've received a ping from that server.
     * Note this method only does something if the address is a LAN address.
     *
     * @param address to check
     */
    void suggestAddress(MysterAddress address);

    void receivedDownNotification(MysterAddress address);
}
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
 * categories (ie it's a good MPG3 sever as well as being just excellent in
 * PORN) that no additional Memory is wasted listing the server TWICE.. It also
 * cuts down on the number of pings a very good server receives.. Objects that
 * get MysterIPs from this pool must call the MysterIP method delete(); so that
 * they can be collected by the MysterIPPool's funky garbage collector.
 */
public interface MysterServerPool {
    /**
     * ONLY UNIT TESTS!
     */
    void suggestAddress(String address);
    
    Optional<MysterIdentity> lookupIdentityFromName(ExternalName externalName);
    Optional<PublicKey> lookupIdentityFromCid(Cid128 cid);
    
    /**
     * @return The MysterServer for this address assuming it's already in the cache. Null otherwise.
     */
    Optional<MysterServer> getCachedMysterServer(MysterIdentity identity);
    Optional<MysterServer> getCachedMysterIp(MysterAddress address);
    
    /**
     * @return true if the MysterServer is in the cache, null otherwise. Danger of race condition
     * between the check and the call to {@link MysterServerPool#getCachedMysterIp(MysterAddress)}
     */
    boolean existsInPool(MysterIdentity identity);
    
    /**
     * @return true if the MysterServer is in the cache, null otherwise. Danger of race condition
     * between the check and the call to {@link MysterServerPool#getCachedMysterIp(MysterAddress)}
     */
    boolean existsInPool(MysterAddress address);

    /**
     * When a new MysterServer is discovered the MysterIpPool is so excited it
     * has to tell anyone who will listen
     * 
     * @param server
     *            that has just been discovered
     */
    void addPoolListener(MysterPoolListener listener);
    void removePoolListener(MysterPoolListener listener);
    
    /**
     * When it's done loading call this
     */
    void clearHardLinks();
    
    void filter(Consumer<MysterServer> consumer);

    /**
     * Call this method if we've received a ping from that server. 
     * Note this method only does something if the address is a LAN address.
     * 
     * @param ip to check
     * @return false if we didn't retry the server.
     */
    void suggestAddress(MysterAddress address);

    void receivedDownNotification(MysterAddress address);
}
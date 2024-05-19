/* 

 Title:			Myster Open Source
 Author:			Andrew Trumper
 Description:	Generic Myster Code
 
 This code is under GPL

 Copyright Andrew Trumper 2000-2001
 */

package com.myster.tracker;

import java.util.function.Consumer;

import com.myster.net.MysterAddress;
import com.myster.type.MysterType;

/**
 * This class exists to make sure that if a server is listed under many
 * categories (ie it's a good MPG3 sever as well as being just excellent in
 * PORN) that no additional Memory is wasted listing the server TWICE.. It also
 * cuts down on the number of pings a very good server receives.. Objects that
 * get MysterIPs from this pool must call the MysterIP method delete(); so that
 * they can be collected by the MysterIPPool's funky garbage collector.
 */
public interface MysterServerPool {
    void suggestAddress(MysterAddress address);
    void suggestAddress(String address);
    
    MysterIdentity lookupIdentityFromName(ExternalName externalName);
    
    /**
     * @return The MysterServer for this address assuming it's already in the cache. Null otherwise.
     */
    MysterServer getCachedMysterServer(MysterIdentity identity);
    MysterServer getCachedMysterIp(MysterAddress address);
    
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
     * When a new MysterServer is dicovered the MysterIpPool is so excited it
     * has to tell anyone who will listen
     * 
     * @param server
     *            that has just been discovered
     */
    void addPoolListener(MysterPoolListener listener);
    void removeNewServerListener(MysterPoolListener listener);
    
    /**
     * When it's done loading call this
     */
    void clearHardLinks();
}
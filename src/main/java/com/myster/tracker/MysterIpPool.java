/* 

 Title:			Myster Open Source
 Author:			Andrew Trumper
 Description:	Generic Myster Code
 
 This code is under GPL

 Copyright Andrew Trumper 2000-2001
 */

package com.myster.tracker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.myster.client.net.MysterProtocol;
import com.myster.mml.MML;
import com.myster.mml.MMLException;
import com.myster.net.MysterAddress;
import com.myster.pref.MysterPreferences;

/**
 * This class exists to make sure that if a server is listed under many
 * categories (ie it's a good MPG3 sever as well as being just excellent in
 * PORN) that no additional Memory is wasted listing the server TWICE.. It also
 * cuts down on the number of pings a very good server receives.. Objects that
 * get MysterIPs from this pool must call the MysterIP method delete(); so that
 * they can be collected by the MysterIPPool's funky garbage collector.
 * 
 */
public interface MysterIpPool {
    /**
     * @return The {@link MysterIP} for this {@link MysterAddress} Blocking if not in pool!
     */
    public MysterServer getMysterServer(MysterAddress address) throws IOException;

    public MysterServer getMysterServer(String name) throws IOException;

    /**
     * @return The MysterServer for this address assuming it's already in the cache. Null otherwise.
     */
    public MysterServer getCachedMysterIp(MysterAddress address);

    
    /**
     * @return true if the MysterServer is in the cache, null otherwise. Danger of race condition
     * between the check and the call to {@link MysterIpPool#getCachedMysterIp(MysterAddress)}
     */
    public boolean existsInPool(MysterAddress s);
}
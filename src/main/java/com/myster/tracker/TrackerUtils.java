
package com.myster.tracker;

import java.lang.ref.Cleaner;
import java.net.InetAddress;

import com.general.thread.Invoker;

public class TrackerUtils {
    static final Cleaner CLEANER = Cleaner.create();
    static final Invoker INVOKER = Invoker.newVThreadInvoker();
    
    static boolean isLanAddress(InetAddress i) {
        return i.isLinkLocalAddress() || i.isSiteLocalAddress(); 
    }
}

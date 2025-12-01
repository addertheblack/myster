
package com.general.net;

import java.net.InetAddress;

public class NetUtils {
    public static boolean isLanAddress(InetAddress i) {
        return i.isLinkLocalAddress() || i.isSiteLocalAddress(); 
    }
}

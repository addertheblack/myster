/*
 * 
 * Title: Myster Open Source Author: Andrew Trumper Description: Generic Myster
 * Code
 * 
 * This code is under GPL
 * 
 * Copyright Andrew Trumper 2000-2001
 */
package com.myster.tracker;


public class IPListManagerSingleton {
    private static IPListManager iplistmanager;

    public IPListManagerSingleton() {
    }

    public static synchronized IPListManager getIPListManager() {
        //return null;
        if (iplistmanager == null)
            iplistmanager = new IPListManager();
        return iplistmanager;
    }

}
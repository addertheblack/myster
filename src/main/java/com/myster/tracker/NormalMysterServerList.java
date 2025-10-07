/* 

 Title:         Myster Open Source
 Author:            Andrew Trumper
 Description:   Generic Myster Code
 
 This code is under GPL

 Copyright Andrew Trumper 2000-2001
 */

/**
 * The IP list is a list of com.myster objects. The idea behind it is that the data type ie: Tree or
 * linked list of array can be changed without affecting the rest of the program.
 */
package com.myster.tracker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import com.myster.type.MysterType;

/**
 * This class implements the MysterServerList interface and provides the
 * implementation that allows for a type based list of file servers. This is in
 * contrast to a LanMysterServerList which is a list of servers that are on the
 * local network.
 * 
 * Yes, this class has a terrible name. It should be called MysterServerListImpl
 * or something similar, but it has been named NormalMysterServerList to
 * indicate that it is the implementation of the MysterServerList
 * that we typically think of when we refer to a MysterServerList.
 */
class NormalMysterServerList implements MysterServerList {
    public static final int LISTSIZE = 100; //Size of any given list..
    
    private static final Logger LOGGER = Logger.getLogger(MysterServerList.class.getName());
    
    private final Map<MysterIdentity, MysterServer> mapOfServers = new LinkedHashMap<>();
    private final MysterType type;
    private final Preferences preferences;
    private final Consumer<MysterType> listChanged;
    
    private MysterServer worstRank = null;
    private long worstTime = 0;


    /**
     * Takes as an argument a list of strings.. These strings are the .toString() product of
     * com.myster objects.
     * @param preferences 
     */
    NormalMysterServerList(MysterType type, MysterServerPool pool, Preferences preferences, Consumer<MysterType> listChanged) {
        this.preferences = preferences;
        this.listChanged = listChanged;

        String s = preferences.get(type.toHexString(), "");
        StringTokenizer externalNames = new StringTokenizer(s);
        int max = externalNames.countTokens();
        for (int i = 0; i < max; i++) {
            try {
                ExternalName externalName = new ExternalName(externalNames.nextToken());
                pool.lookupIdentityFromName(externalName)
                    .filter(identity -> pool.existsInPool(identity))
                    .flatMap(identity -> pool.getCachedMysterServer(identity))
                    .ifPresentOrElse(
                        server -> mapOfServers.put(server.getIdentity(), server),
                        () -> LOGGER.warning("This server does not exist in the pool: " + externalName + ". Repairing.")
                    );
            } catch (Exception ex) {
                LOGGER.warning("Failed to add an IP to an IP list: " + type + " " + ex);
            }
        }

        this.type = type;
        sort();
    }

    /**
     * Returns a String array of length the requested number of entries. Note: It's possible for the
     * list to have fewer entries than requested.. IN that case the rest of the array will be null.
     * 
     * This function will not return any items from the list that aren't currently "up" ie: That
     * cannot be connected to because they are down or the user isn't connected to the internet.
     */
    public synchronized MysterServer[] getTop(int x) {
        List <MysterServer> servers = new ArrayList<>();

        for (MysterServer value : mapOfServers.values()) {
            if (value.getStatus() && (!value.isUntried())) {
                servers.add(value);
            }
        }
        return servers.toArray(MysterServer[]::new);
    }

    /**
     * Returns vector of MysterAddress.
     */
    public synchronized List<MysterServer> getAll() {
        return new ArrayList<>(mapOfServers.values());
    }

    /**
     * This function adds an IP to the IP List.
     */
    public synchronized void addIP(MysterServer ip) {
        insertionSort(ip);
    }

    public MysterType getType() {
        return type;
    }

    /**
     * Modifies the preferences and saves the changes.
     */
    private synchronized void save() {
        StringBuffer buffer = new StringBuffer();
        for (MysterServer server : mapOfServers.values()) {
            buffer.append("" + server.getExternalName() + " ");
        }

        preferences.put(type.toHexString(), buffer.toString());
        try {
            preferences.flush();
        } catch (BackingStoreException exception) {
            exception.printStackTrace();
        }
        
    }

    /**
     * insertionSort adds an IP to the list.. the list currently uses an array and insert into the
     * list using insertion sort. It also checks to make sure the same place isn't put in twice.
     */
    private synchronized void insertionSort(MysterServer ip) {
        if (ip == null)
            return;
        if (mapOfServers.containsKey(ip.getIdentity())) {
            return;
        }
        
        // cache expired
        // we need this because rank changes in the background.
        // so we count 30 secs before we stop trusting it
        if (System.currentTimeMillis() - worstTime > 30000) {
            worstRank = null;
        }
        
        // to avoid punishing re-sorts
        if(worstRank != null && mapOfServers.size() >= LISTSIZE && worstRank.getRank(type) > ip.getRank(type)) {
            return;
        }
        
        mapOfServers.put(ip.getIdentity(), ip);
        sort();
        save();
        
        listChanged.accept(type);
    }

    private synchronized void sort() {
        List<MysterServer> servers = new ArrayList<>(mapOfServers.values());
        
        // Take a snapshot of the ranks before sorting.
        // getRank() is not stable across invocations. 
        Map<MysterServer, Double> rankSnapshot = new HashMap<>();
        for (MysterServer server : servers) {
            rankSnapshot.put(server, server.getRank(type) * 10);
        }

        // Sort based on the snapshot values..
        servers.sort((a, b) -> {
            return Integer.compare((int)(double)rankSnapshot.get(a), (int)(double)rankSnapshot.get(b));
        });
        
        mapOfServers.clear();
        for (MysterServer s: servers) {
            if (mapOfServers.size() >= LISTSIZE) {
                return;
            }
            
            mapOfServers.put(s.getIdentity(), s);
            
            worstRank = s;
            worstTime = System.currentTimeMillis();
        }
    }

    public synchronized void notifyDeadServer(MysterIdentity identity) {
        if (mapOfServers.remove(identity) != null) {
            save();
            listChanged.accept(type);
        }
    }
}
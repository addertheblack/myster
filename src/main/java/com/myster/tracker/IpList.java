/* 

 Title:			Myster Open Source
 Author:			Andrew Trumper
 Description:	Generic Myster Code
 
 This code is under GPL

 Copyright Andrew Trumper 2000-2001
 */

/**
 * The IP list is a list of com.myster objects. The idea behind it is that the data type ie: Tree or
 * linked list of array can be changed without affecting the rest of the program.
 * 
 * 
 *  
 */

package com.myster.tracker;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import com.myster.net.MysterAddress;
import com.myster.type.MysterType;

class IpList {
    public static final int LISTSIZE = 100; //Size of any given list..
    
    private final Map<MysterAddress, MysterServer> mapOfServers = new LinkedHashMap<>();
    private final MysterType type;
    private final Preferences preferences;
    
    private MysterServer worstRank = null;
    private long worstTime = 0;

    /**
     * Takes as an argument a list of strings.. These strings are the .toString() product of
     * com.myster objects.
     * @param preferences 
     */
    protected IpList(MysterType type, MysterIpPool pool, Preferences preferences) {
        this.preferences = preferences;

        String s = preferences.get(type.toString(), "");
        StringTokenizer ips = new StringTokenizer(s);
        int max = ips.countTokens();
        for (int i = 0; i < max; i++) {
            try {
                MysterServer temp = null;
                String workingip = ips.nextToken();
                if (pool.existsInPool(new MysterAddress(workingip))) {
                    try {
                        temp = pool.getMysterServer(
                                new MysterAddress(workingip));
                    } catch (UnknownHostException ex) {
                        // do nothing
                    }
                }//if IP doens't exist in the pool, remove it from the list!
                if (temp == null) {
                    System.out.println("Found a list bubble: " + workingip + ". Repairing.");
                    continue;
                }

                mapOfServers.put(temp.getAddress(), temp);
            } catch (Exception ex) {
                System.out.println("Failed to add an IP to an IP list: " + type);
            }
        }

        this.type = type;
        sort();
        removeCrap();
    }

    /**
     * Returns a String array of length the requested number of entries. Note: It's possible for the
     * list to have fewer entries than requested.. IN that case the rest of the array will be null.
     * 
     * This function will not return any items from the list that aren't currently "up" ie: That
     * cannot be connected to because they are down or the user isn't connected to the internet.
     */
    public synchronized MysterServer[] getTop(int x) {
        MysterServer[] temp = new MysterServer[x];

        //save();
        //io.writeIPList(getAsArray());

        int counter = 0;
        
        for (MysterServer value : mapOfServers.values()) {
            if (value.getStatus() && (!value.isUntried())) {
                temp[counter] = value;
                counter++;
            }
            
            if (counter >= temp.length) {
                return temp;
            }
        }
        return temp;
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
    protected synchronized void addIP(MysterServer ip) {
        insertionSort(ip);
    }

    public MysterType getType() {
        return type;
    }

    private synchronized void removeCrap() {
        Iterator<Map.Entry<MysterAddress, MysterServer>> iterator = mapOfServers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<MysterAddress, MysterServer> entry = iterator.next();
            if (entry.getKey().getIP().equals("127.0.0.1")) {
                iterator.remove();
            }
        }

    }

    /**
     * Modifies the preferences and saves the changes.
     */
    private synchronized void save() {
        removeCrap();

        StringBuffer buffer = new StringBuffer();  
        for (MysterAddress a: mapOfServers.keySet()) {
            buffer.append("" + a + " ");
        }

        preferences.put(type.toString(), buffer.toString());
        try {
            System.out.println(preferences.keys()[0]);
            System.out.println(preferences.get(type.toString(), ""));
            preferences.flush();
        } catch (BackingStoreException exception) {
            // TODO Auto-generated catch block
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
        if (mapOfServers.containsKey(ip.getAddress())) {
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
        
        mapOfServers.put(ip.getAddress(), ip);
        sort();
        save();
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
            mapOfServers.put(s.getAddress(), s);
            
            
            worstRank = s;
            worstTime = System.currentTimeMillis();
        }
    }

}
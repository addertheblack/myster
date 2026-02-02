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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import com.general.events.NewGenericDispatcher;
import com.myster.net.MysterAddress;
import com.myster.net.datagram.client.PingResponse;
import com.myster.net.server.ServerUtils;
import com.myster.type.MysterType;
import com.myster.type.TypeDescription;
import com.myster.type.TypeDescriptionEvent;
import com.myster.type.TypeDescriptionList;
import com.myster.type.TypeListener;

/**
 * This class is the interface to Myster's tracker. Every single interaction
 * with the tracker module inside Myster currently goes through here. The
 * tracker is the part of Myster that keeps track of the list of servers that
 * Myster knows about. Basically it maintains the list of the top XXX number of
 * servers on the network for a given file type. All the servers kept by the
 * tracker have associated misc. statistics about themselves kept. These
 * statistics are kept current n a best effort basis. These statistics are used
 * to generate a "rank". This "rank" determines if the server is to be kept
 * about in memory on one of the server lists.
 * <p>
 * To access this object from Myster code use the singleton :
 * com.myster.tracker.IPListManagerSingleton
 * <p>
 * The Tracker now supports dynamic type addition/removal by listening for
 * TypeEnabled and TypeDisabled events from the TypeDescriptionList.
 *
 * @see com.myster.tracker.IPListManagerSingleton
 */
public class Tracker {
    private static final Logger log = Logger.getLogger(Tracker.class.getName());
    private static final String[] LAST_RESORT = { "myster.ddnsgeek.com" };
    private static final String PATH = "ServerLists";

    private final List<ServerList> list;
    private final LanMysterServerList lan;
    private final BookmarkMysterServerList bookmarks;
    private final List<TypeDescription> enabledTypes;
    private final MysterServerPool pool;
    private final Preferences preferences;
    private final NewGenericDispatcher<ListChangedListener> dispatcher;
    private final TypeDescriptionList tdList;

    public interface ListChangedListener {
        public void serverAddedRemoved(MysterType type);
        
        public void lanServerAddedRemoved();
        
        public void bookmarkServerAddedRemoved();
    }
    
    public Tracker(MysterServerPool pool, Preferences preferences, TypeDescriptionList typeDescriptionList) {
        this.pool = pool;
        this.preferences = preferences.node(PATH);
        this.dispatcher = new NewGenericDispatcher<>(ListChangedListener.class, TrackerUtils.INVOKER);

        TypeDescription[] enabledTypesArray = typeDescriptionList.getEnabledTypes();
        this.enabledTypes = new ArrayList<>(List.of(enabledTypesArray));
        tdList = typeDescriptionList;

        // Register as a listener for type enable/disable events
        tdList.addTypeListener(new TypeListnerImpl());

        this.list = new ArrayList<>();
        for (int i = 0; i < this.enabledTypes.size(); i++) {
            assertIndex(i); // loads all lists.
        }
        
        lan = new LanMysterServerList(pool, dispatcher.fire()::lanServerAddedRemoved);
        bookmarks = new BookmarkMysterServerList(pool, this.preferences, dispatcher);

        pool.addPoolListener(new MysterPoolListener() {
            @Override
            public void serverRefresh(MysterServer server) {
                addServerToAllLists(server);
            }
            
            @Override
            public void serverPing(PingResponse server) {
                lan.serverPing(server.address(), !server.isTimeout());
            }

            @Override
            public void deadServer(MysterIdentity identity) {
                notifyAllListsDeadServer(identity);
            }
        });
        
        pool.clearHardLinks();

        
        // since we''ve missed events while the tracker was being constructed
        // we need to recheck the pool for lan servers
        pool.forEach(server -> {
            MysterAddress[] upAddresses = server.getUpAddresses();
            for (MysterAddress address : upAddresses) {
                if (ServerUtils.isLanAddress(address.getInetAddress())) {
                    lan.addIP(server);
                    break; // only add once
                }
            }
        });
    }
    
    private void notifyAllListsDeadServer(MysterIdentity identity) {
        list.forEach(l -> l.notifyDeadServer(identity));
        bookmarks.notifyDeadServer(identity);
    }

    /**
     * This routine is used to suggest an ip for the tracker to add to its
     * server lists. The suggested ip will not show up on the tracker's lists
     * until it has had its statistics queried. This can take a while. THIS
     * ROUTINE IS NONE BLOCKING so the caller doens't have to worry about a
     * lengthy delay while the server is queried for its statistics.
     * 
     * @param ip
     *            The MysterAddress of the server you want to add.
     */
    public void addIp(MysterAddress ip) {
        pool.getCachedMysterServer(ip).ifPresentOrElse(s -> {
            if (!s.isUntried() && s.getUpAddresses().length == 0) {
                pool.suggestAddress(ip);
            }
        }, () -> pool.suggestAddress(ip));
    }
    
    
    /**
     * Call this method if we've received a notification that a server is "up" from
     * either getting a ping (original purpose) or a notification from a resource 
     * discovery service that the server is on the network and is "up" 
     */
    public void receivedPing(MysterAddress ip) {
        // this doens't always work because the ping can come from a port that
        // isn't the same one
        // that the server is registered with. In fact this is the normal case
        // for servers on a different port.
        // This will cause the cache lookup
        // to fail. So we ignore this case for servers not on the LAN.
        // For LAN addresses we look for servers on alternate addresses and
        // check
        // which are down and then ping that
        // This could result in extra pings but whatever. It's on the LAN
        // anyway.
        // For servers on a LAN we use the default port to allow servers to be
        // discoverable.. So this code path is a nice to have.
        if (!ServerUtils.isLanAddress(ip.getInetAddress())) {
            return;
        }
        
       pool.suggestAddress(ip);
    }
    
    public void addressIsGoingDown(MysterAddress ip) {
        pool.receivedDownNotification(ip);
    }
    
    /**
     * Calls getTop(type, 10).
     */
    public synchronized MysterServer[] getTopTen(MysterType type) {
        return getTop(type, 10);
    }

    /**
     * Returns a list of Myster servers ordered by rank. Returns only server
     * currently thought to be available (up). If there are not enough UP
     * Servers or whatever, the rest of the array is filled with null!
     * 
     * @param type
     *            to return servers for
     * @param x
     *            number of servers to try and return
     * @return an array of MysterServer objects ordered by rank and possibly
     *         containing nulls.
     */
    public synchronized MysterServer[] getTop(MysterType type, int x) {
        ServerList iplist;
        iplist = getListFromType(type);
        if (iplist == null)
            return null;
        return iplist.getTop(x);

    }

    /**
     * Asks the cache if it knows of this MysterServer and gets stats if it does
     * else returns null. Does not do any io. Returns quickly.
     * 
     * @return Myster server at that address or null if the tracker doesn't have
     *         any record of a server at that address
     */
    public synchronized MysterServer getQuickServerStats(MysterAddress address) {
        return pool.getCachedMysterServer(address).orElse(null);
    }

    /**
     * Returns vector of ALL MysterAddress object in order of rank for that
     * type.
     * 
     * @return Vector of MysterAddresses in the order of rank.
     */
    public synchronized List<MysterServer> getAll(MysterType type) {
        ServerList iplist;
        iplist = getListFromType(type);
        if (iplist == null)
            return null;
        return iplist.getAll();
    }
    
    public synchronized List<MysterServer> getAllLan() {
        return lan.getAll();
    }

    /**
     * Returns an array of string objects representing a set of servers that
     * could be available for bootstrapping onto the Myster network.
     * 
     * @return an array of string objects representing internet addresses
     *         (ip:port or domain name:port format)
     */
    public static String[] getOnRamps() {
        return LAST_RESORT.clone();
    }
    
    public void addPoolListener(MysterPoolListener l) {
        pool.addPoolListener(l);
    }

    /**
     * Returns all bookmarked servers.
     * 
     * @return List of bookmarked servers
     */
    public synchronized List<MysterServer> getAllBookmarks() {
        return bookmarks.getAll();
    }

    /**
     * Adds or updates a bookmark.
     * 
     * @param bookmark The bookmark to add or update
     */
    public synchronized void addBookmark(BookmarkMysterServerList.Bookmark bookmark) {
        bookmarks.addBookmark(bookmark);
    }

    /**
     * Removes a server from bookmarks.
     * 
     * @param identity The identity of the server to remove from bookmarks
     */
    public synchronized void removeBookmark(MysterIdentity identity) {
        bookmarks.removeBookmark(identity);
    }

    /**
     * Gets the bookmark for a bookmarked server.
     * 
     * @param identity The identity of the server
     * @return Optional containing the bookmark if the server is bookmarked
     */
    public synchronized Optional<BookmarkMysterServerList.Bookmark> getBookmark(MysterIdentity identity) {
        return bookmarks.getBookmark(identity);
    }

    /**
     * This routine is here so that the ADDIP callback can try to add a
     * MysterSever to all lists
     * 
     * @param server
     *            to try to add to all lists. Server will not be added if it is
     *            unworthy of being on the list
     */
    private void addServerToAllLists(MysterServer server) {
        for (int i = 0; i < enabledTypes.size(); i++) {
            assertIndex(i);
            list.get(i).addIP(server);
        }

        lan.addIP(server);
    }

    /**
     * This function looks returns a IPList for the type passed if such a list
     * exists. If no such list exists it returns null.
     * 
     * @param type
     *            of list to fetch
     * @return the IPList for the type or null if no list exists for that typ.
     */

    private ServerList getListFromType(MysterType type) {
        int index;
        index = getIndex(type);

        if (index == -1)
            return null;

        assertIndex(index); // to make sure the list if loaded.

        if (list.get(index).getType().equals(type))
            return list.get(index);

        return null;
    }

    /**
     * For lazy loading of server lists. Thread safe.
     * During construction, this creates lists on-demand.
     * After construction, with dynamic type enable/disable, lists should already exist.
     */
    private synchronized void assertIndex(int index) {
        // Expand list if needed
        while (list.size() <= index) {
            list.add(null);
        }

        // Create list if null (lazy loading during construction)
        if (list.get(index) == null) {
            list.set(index, createNewList(index));
            log.info("Loaded list for type: " + enabledTypes.get(index).getDescription());
        }
    }

    /**
     * Returns the index in the list of IPLists for the type passed.
     *
     * @param type
     * @return the index in the list array for this type or -1 if there is not
     *         list for this type.
     */
    private synchronized int getIndex(MysterType type) {
        for (int i = 0; i < enabledTypes.size(); i++) {
            if (enabledTypes.get(i).getType().equals(type))
                return i;
        }

        return -1;
    }

    /**
     * Returns an IPList for the type in the tdlist variable for that index.
     * This is a stupid routine.
     */
    private synchronized ServerList createNewList(int index) {
        return new MysterTypeServerList(enabledTypes.get(index).getType(), pool, preferences, dispatcher.fire()::serverAddedRemoved);
    }

    public void addListChangedListener(ListChangedListener l) {
        dispatcher.addListener(l);
    }

    /**
     * Called when a type is enabled in the TypeDescriptionList.
     * Creates a new MysterTypeServerList and populates it with existing servers
     * that know about this type.
     */
    private void typeEnabled(TypeDescriptionEvent e) {
        MysterType newType = e.getType();

        // Only add if still enabled (could have been toggled rapidly)
        Optional<TypeDescription> descOpt = tdList.get(newType);
        if (descOpt.isEmpty() || !tdList.isTypeEnabled(newType)) {
            return;
        }

        TypeDescription desc = descOpt.get();

        // Add to enabledTypes list
        synchronized(this) {
            // Check if already present (shouldn't be, but defensive)
            if (getIndex(newType) != -1) {
                log.warning("Type already enabled in tracker: " + newType);
                return;
            }

            enabledTypes.add(desc);

            // Create new MysterTypeServerList
            ServerList newList = new MysterTypeServerList(
                newType, pool, preferences,
                dispatcher.fire()::serverAddedRemoved
            );

            // Populate with existing servers that know about this type
            pool.forEach(server -> {
                if (server.knowsAboutType(newType)) {
                    newList.addIP(server);
                }
            });

            list.add(newList);

            log.info("Enabled type in tracker: " + desc.getDescription() + " (" + newType + ")");
        }

        // Notify listeners
        dispatcher.fire().serverAddedRemoved(newType);
    }

    /**
     * Called when a type is disabled in the TypeDescriptionList.
     * Removes the MysterTypeServerList and cleans up preferences.
     */
    private void typeDisabled(TypeDescriptionEvent e) {
        MysterType type = e.getType();

        synchronized(this) {
            int index = getIndex(type);
            if (index == -1) {
                return; // Not in our list (already removed or never added)
            }

            // Remove from lists
            list.remove(index);
            enabledTypes.remove(index);

            // Clean up preferences
            try {
                String nodeName = type.toHexString();
                if (preferences.nodeExists(nodeName)) {
                    preferences.node(nodeName).removeNode();
                    preferences.flush();
                }
                log.info("Disabled type in tracker and cleaned up preferences: " + type);
            } catch (BackingStoreException ex) {
                log.warning("Failed to remove preferences for type: " + type + " - " + ex.getMessage());
            }
        }

        // Notify listeners
        dispatcher.fire().serverAddedRemoved(type);
    }

    private class TypeListnerImpl implements TypeListener {
        @Override
        public void typeEnabled(TypeDescriptionEvent e) {
            Tracker.this.typeEnabled(e);
        }

        @Override
        public void typeDisabled(TypeDescriptionEvent e) {
            Tracker.this.typeDisabled(e);
        }
    }
}


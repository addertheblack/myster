package com.myster.tracker;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.myster.net.MysterAddress;
import com.myster.net.server.ServerUtils;

/**
 * LanMysterServerList is a list of MysterServer objects that are on the local network.
 * It does not save itself in preferences, does not sort its list, and does not have a MysterType.
 */
class LanMysterServerList {
//    private static final Logger LOGGER = Logger.getLogger(LanMysterServerList.class.getName());

    private final List<MysterServer> lanServers = new ArrayList<>();
    private final Runnable listChangedListener;
    private final MysterServerPool pool;

    /**
     * Constructor for LanMysterServerList.
     *
     * @param listChangedListener A Runnable that is executed whenever the list changes.
     */
    LanMysterServerList(MysterServerPool pool, Runnable listChangedListener) {
        this.listChangedListener = listChangedListener;
        this.pool = pool;
    }

    /**
     * Adds a MysterServer to the list if it has a LAN address and is "UP".
     *
     * @param server
     *            The MysterServer to add.
     */
    public synchronized void addIP(MysterServer server) {
        MysterAddress[] upAddresses = server.getUpAddresses();
        for (MysterAddress address : upAddresses) {
            updateServer(address, true);
        }
    }

    /**
     * Returns all servers in the list.
     *
     * @return A list of all MysterServer objects.
     */
    public synchronized List<MysterServer> getAll() {
        return new ArrayList<>(lanServers);
    }

    /**
     * Returns the top servers in the list. This is the same as getAll().
     *
     * @param x The number of servers to return (ignored in this implementation).
     * @return A list of all MysterServer objects.
     */
    public synchronized MysterServer[] getTop(int x) {
        return lanServers.toArray(MysterServer[]::new);
    }

    public void serverPing(MysterAddress address, boolean isUp) {
        updateServer(address, isUp);
    }

    private void updateServer(MysterAddress address, boolean isUp) {
        if (!ServerUtils.isLanAddress(address.getInetAddress())) {
            return;
        }

        Optional<MysterServer> mysterServerOptional = pool.getCachedMysterIp(address);
        
        if (mysterServerOptional.isEmpty()) {
            return;
        }
        
        MysterServer mysterServer = mysterServerOptional.get();

        synchronized (this) {
            if (isUp) {
                // Find the myster server in the list by its identity. If it's
                // not present then add it.
                // note we can't do lanServers.contains(mysterServer) because
                // there's no equals defined on mysterServer

                if (!isMysterServerPresent(lanServers, mysterServer)) {
                    lanServers.add(mysterServer);

                    listChangedListener.run();
                }
            } else {
                lanServers.removeIf(s -> s.getIdentity().equals(mysterServer.getIdentity()));

                listChangedListener.run();
            }
        }
    }
    
    private static boolean isMysterServerPresent(List<MysterServer> list, MysterServer server) {
        for (MysterServer s : list) {
            if (s.getIdentity().equals(server.getIdentity())) {
                return true;
            }
        }
        
        return false;
    }
}

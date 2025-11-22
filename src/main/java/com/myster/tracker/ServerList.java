package com.myster.tracker;

import java.util.List;
import com.myster.type.MysterType;

/**
 * Interface for MysterServerList implementations.
 */
interface ServerList {
    /**
     * Returns a String array of length the requested number of entries. Note: It's possible for the
     * list to have fewer entries than requested.. IN that case the rest of the array will be null.
     *
     * This function will not return any items from the list that aren't currently "up" ie: That
     * cannot be connected to because they are down or the user isn't connected to the internet.
     */
    MysterServer[] getTop(int x);

    /**
     * Returns all known servers
     */
    List<MysterServer> getAll();

    /**
     * This function adds an IP to the IP List.
     */
    void addIP(MysterServer ip);

    /**
     * Returns the type of the server list.
     */
    MysterType getType();

    /**
     * Notifies the list that a server has no more addresses associated with it
     * and is therefore being deleted from the know set of all servers.
     */
    void notifyDeadServer(MysterIdentity identity);
}
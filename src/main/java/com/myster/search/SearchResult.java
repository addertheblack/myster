package com.myster.search;

import com.myster.net.MysterAddress;
import com.myster.net.client.MysterProtocol;
import com.myster.tracker.MysterServer;

public interface SearchResult {
    // is called when the user decides to download the item
    void download();

    void downloadTo();

    // returns the network the search result is on.
    String getNetwork();

    // gets a value for a meta data thingy
    String getMetaData(String key);

    // gets the list of known meta data types for this item.
    String[] getKeyList();

    // gets the Name of the search result (usualy a file name!)
    String getName();

    // gets the host address
    MysterAddress getHostAddress();

    MysterServer getServer();

    // this might be an abstraction violation.. I'm not sure.
    MysterProtocol getProtocol();
}
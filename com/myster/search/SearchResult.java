package com.myster.search;

import com.myster.net.MysterAddress;
import com.myster.tracker.MysterServer;

public interface SearchResult {

    //is called when the user decides to download the item
    public void download();

    //returns the network the search result is on.
    public String getNetwork();

    //gets a value for a meta data thingy
    public String getMetaData(String key);

    //gets the list of known meta data types for this item.
    public String[] getKeyList();

    //gets the Name of the search result (usualy a file name!)
    public String getName();

    //gets the host address
    public MysterAddress getHostAddress();
    
    public MysterServer getServer();
}
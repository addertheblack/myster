package com.myster.search;

import com.myster.net.MysterAddress;

public class HashSearchResult implements SearchResult {
    final MysterFileStub stub;

    public HashSearchResult(MysterFileStub stub) {
        this.stub = stub;
    }

    public MysterFileStub getFileStub() {
        return stub;
    }

    //is called when the user decides to download the item
    public void download() {
        //not implemented.
    }

    //returns the network the search result is on.
    public String getNetwork() {
        return "Myster";
    }

    //gets a value for a meta data thingy
    public String getMetaData(String key) {
        return null;
    }

    //gets the list of known meta data types for this item.
    public String[] getKeyList() {
        return new String[] {};
    }

    //gets the Name of the search result (usualy a file name!)
    public String getName() {
        return stub.getName();
    }

    //gets the host address
    public MysterAddress getHostAddress() {
        return stub.getMysterAddress();
    }
}
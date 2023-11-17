package com.myster.search;

import java.util.ArrayList;
import java.util.List;


import com.myster.mml.RobustMML;
import com.myster.net.MysterAddress;
import com.myster.search.ui.ServerStatsFromCache;
import com.myster.tracker.MysterServer;

public class MysterSearchResult implements SearchResult {
    private RobustMML mml;

    private final MysterFileStub stub;
    
    private final ServerStatsFromCache cache;

    public MysterSearchResult(MysterFileStub stub, ServerStatsFromCache cache) {
        this.stub = stub;
        this.cache = cache;
    }

    public void setMML(RobustMML m) {
        mml = m;
    }

    //is called when the user decides to download the item
    public void download() {
        com.myster.client.stream.StandardSuite.downloadFile(stub
                .getMysterAddress(), stub);
    }

    //returns the network the search result is on.
    public String getNetwork() {
        return "Myster Network";
    }

    //gets a value for a meta data thingy
    public String getMetaData(String key) {
        return (mml == null ? null : mml.get(key));
    }

    //gets the list of known meta data types for this item.
    public String[] getKeyList() {
        if (mml == null)
            return new String[] {};

        List<String> items = mml.list("/");

        List<String> v_temp = new ArrayList<>(items.size());

        for (int i = 0; i < items.size(); i++) {
            String s_temp = (items.get(i));
            if (mml.isAFile("/" + s_temp)) {
                v_temp.add("/" + s_temp);
            }
        }

        String[] sa_temp = new String[v_temp.size()];

        for (int i = 0; i < v_temp.size(); i++) {
            sa_temp[i] = v_temp.get(i);
        }

        return sa_temp;
    }

    //gets the Name of the search result (usualy a file name!)
    public String getName() {
        return stub.getName();
    }

    //gets the host address
    public MysterAddress getHostAddress() {
        return stub.getMysterAddress();
    }

    @Override
    public MysterServer getServer() {
        return cache.get(getHostAddress());
    }
}
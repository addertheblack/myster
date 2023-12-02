package com.myster.search;

import java.util.ArrayList;
import java.util.List;

import com.myster.client.net.MysterProtocol;
import com.myster.mml.RobustMML;
import com.myster.net.MysterAddress;
import com.myster.search.ui.ServerStatsFromCache;
import com.myster.tracker.MysterServer;

public class MysterSearchResult implements SearchResult {
    private final MysterFileStub stub;
    private final ServerStatsFromCache cache;
    private final MysterProtocol protocol;
    private final HashCrawlerManager hashCrawler;
    
    private RobustMML mml;

    public MysterSearchResult(MysterProtocol protocol,
                              HashCrawlerManager hashCrawler,
                              MysterFileStub stub,
                              ServerStatsFromCache cache) {
        this.protocol = protocol;
        this.hashCrawler = hashCrawler;
        this.stub = stub;
        this.cache = cache;
    }

    public void setMML(RobustMML m) {
        mml = m;
    }

    // is called when the user decides to download the item
    @Override
    public void download() {
        getProtocol().getStream().downloadFile(hashCrawler, stub.getMysterAddress(), stub);
    }

    //returns the network the search result is on.
    @Override
    public String getNetwork() {
        return "Myster Network";
    }

    //gets a value for a meta data thingy
    @Override
    public String getMetaData(String key) {
        return (mml == null ? null : mml.get(key));
    }

    //gets the list of known meta data types for this item.
    @Override
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

    //gets the filename of the search result
    @Override
    public String getName() {
        return stub.getName();
    }

    @Override
    public MysterAddress getHostAddress() {
        return stub.getMysterAddress();
    }

    @Override
    public MysterServer getServer() {
        return cache.get(getHostAddress());
    }

    @Override
    public MysterProtocol getProtocol() {
        return protocol;
    }
}
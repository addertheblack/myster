/*
 * 
 * Title: Myster Open Source Author: Andrew Trumper Description: Generic Myster Code
 * 
 * This code is under GPL
 * 
 * Copyright Andrew Trumper 2000-2004
 * 
 * 
 *  
 */
package com.myster.search;

import com.myster.client.net.MysterProtocol;
import com.myster.tracker.MysterServerManager;
import com.myster.type.MysterType;
import com.myster.ui.MysterFrameContext;
import com.myster.util.MysterThread;
import com.myster.util.Sayable;


/**
 * This class is responsible for starting/stopping all searches. Currently it only supports
 * Myster searches but could be modified to search using multiple different protocols.
 */
public class SearchEngine extends MysterThread {
    private final MysterSearch mysterSearch;

    public SearchEngine(MysterProtocol protocol,
                        HashCrawlerManager hashManager,
                        MysterFrameContext context,
                        MysterServerManager ipListManager,
                        SearchResultListener listener,
                        Sayable msg,
                        MysterType type,
                        String searchString ) {
        mysterSearch = new MysterSearch(protocol,
                                        hashManager,
                                        context,
                                        ipListManager,
                                        listener,
                                        msg,
                                        type,
                                        searchString);
    }

    public void run() {
        mysterSearch.run();
    }

    public void end() {
        flagToEnd();
        try {
            join();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    public void flagToEnd() {
        mysterSearch.flagToEnd();
    }
}
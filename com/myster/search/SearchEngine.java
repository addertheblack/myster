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

import com.myster.search.ui.SearchWindow;
import com.myster.tracker.IPListManager;
import com.myster.util.MysterThread;


/**
 * This class is responsible for starting/stopping all searches. Currently it only supports
 * Myster searches but could be modified to search using multiple different protocols.
 */
public class SearchEngine extends MysterThread {
    private final SearchWindow window;

    private final MysterSearch mysterSearch;

    public SearchEngine(SearchWindow w, IPListManager manager) {
        window = w;
        mysterSearch = new MysterSearch(window, window, window.getMysterType(), window.getSearchString(), manager);
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
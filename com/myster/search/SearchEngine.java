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

import com.general.util.TextSpinner;
import com.general.util.UnexpectedException;
import com.general.util.Util;
import com.myster.search.ui.SearchWindow;
import com.myster.util.MysterThread;


/**
 * This class is responsible for starting/stopping all searches. Currently it only supports
 * Myster searches but could be modified to search using multiple different protocols.
 */
public class SearchEngine extends MysterThread {
    SearchWindow window;

    MysterSearch msearch;

    public SearchEngine(SearchWindow w) {
        window = w;
        msearch = new MysterSearch(window, window, window.getType(), window.getSearchString());
    }

    public void run() {
        msearch.run();
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
        msearch.flagToEnd();
    }
}
/*
 * 
 * Title: Myster Open Source Author: Andrew Trumper Description: Generic Myster Code
 * 
 * This code is under GPL
 * 
 * Copyright Andrew Trumper 2000-2001
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

public class SearchEngine extends MysterThread {
    SearchWindow window;

    MysterSearch msearch;

    public SearchEngine(SearchWindow w) {
        window = w;
        msearch = new MysterSearch(window, window, window.getType(), window.getSearchString());
    }

    public void run() {
        try {
            Util.invokeAndWait(new Runnable() {
                public void run() {
                    window.startSearch();
                }
            });
        } catch (InterruptedException ex) {
            // TODO Auto-generated catch block
            throw new UnexpectedException(ex);
        }

        msearch.start();

        try {
            msearch.waitForEnd();
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        try {
            Util.invokeAndWait(new Runnable() {
                public void run() {
                    window.searchOver();
                }
            });
        } catch (InterruptedException ex) {
            // TODO Auto-generated catch block
            throw new UnexpectedException(ex);
        }
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
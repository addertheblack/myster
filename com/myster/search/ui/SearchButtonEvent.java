/*
 * 
 * Title: Myster Open Source Author: Andrew Trumper Description: Generic Myster Code
 * 
 * This code is under GPL
 * 
 * Copyright Andrew Trumper 2000-2001
 * 
 *  
 */

package com.myster.search.ui;

import java.awt.Button;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import com.general.util.TextSpinner;
import com.myster.search.SearchEngine;
import com.myster.util.MysterThread;

public class SearchButtonEvent implements ActionListener {
    private final SearchWindow searchWindow;

    private final Button searchButton;

    private SearchEngine sengine;

    boolean flag = false;

    /**
     * The search Window must be passed since This event handler needs to know which File type is
     * selected. <b>It asks the window... </b>.. Not to mention the search string
     * 
     * Note: At this writting this has not been implemented.. ie: The Handler doens't recognized
     * types.
     */
    public SearchButtonEvent(SearchWindow searchWindow, Button searchButton) {
        this.searchWindow = searchWindow;
        this.searchButton = searchButton;
    }

    /**
     * Handles resquest for action, action, action, at the sringfield speedway, speedway, speedway.
     * This routines needs to be redone.. the code is not pretty enough. Don't I deserve pretty
     * code??? This routine starts up n new search thread where n is the number of active ips in the
     * ip list. IF the are no iactive ips in the ip list, Myster goes to the array of last resort
     * for some possible active IPs to add. This saves the user from adding ips manually at the
     * start.
     */
    public void actionPerformed(ActionEvent event) {
        if (!searchButton.isEnabled())
            return;

        if (searchButton.getLabel().equals("Stop")) {
            searchButton.setEnabled(false);
            sengine.flagToEnd();
        } else {
            searchButton.setEnabled(false);
            searchButton.setLabel("Stop");
            sengine = new SearchEngine(searchWindow);
            sengine.start();
        }
    }

    //    private class Waiter implements Runnable {
    //        boolean dieflag = false;
    //
    //        TextSpinner s = new TextSpinner();
    //
    //        public void run() {
    //            searchWindow.say("Waiting for searches to cancel (this may take a while) " + s.getSpin());
    //
    //        }
    //    }
}


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

import com.myster.search.SearchEngine;

public class SearchButtonEvent implements ActionListener {
    private final SearchWindow searchWindow;

    private final Button searchButton;

    private SearchEngine sengine;

    boolean flag = false;

    /**
     * The search Window must be passed since This event handler needs to know which File type is
     * selected. <b>It asks the window... </b>.. Not to mention the search string
     */
    public SearchButtonEvent(SearchWindow searchWindow, Button searchButton) {
        this.searchWindow = searchWindow;
        this.searchButton = searchButton;
    }

    /**
     * Starts / stops the search. This code is called by the text field and search button.
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
            searchWindow.setTitle("Search for \"" + searchWindow.getSearchString() + "\"");
        }
    }
}


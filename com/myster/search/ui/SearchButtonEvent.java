/*
 * 
 * Title: Myster Open Source Author: Andrew Trumper Description: Generic Myster
 * Code
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

public class SearchButtonEvent implements ActionListener {
    private final SearchWindow searchWindow;

    /**
     * The search Window must be passed since This event handler needs to know
     * which File type is selected. <b>It asks the window... </b>.. Not to
     * mention the search string
     */
    public SearchButtonEvent(final SearchWindow searchWindow, final Button searchButton) {
        this.searchWindow = searchWindow;
    }

    /**
     * Starts / stops the search. This code is called by the text field and
     * search button.
     */
    public void actionPerformed(final ActionEvent event) {
        final Button searchButton = (Button) event.getSource();

        if (!searchButton.isEnabled())
            return;

        if (searchButton.getLabel().equals("Stop")) {
            searchWindow.stopSearch();
        } else {
            searchWindow.startSearch();
        }
    }
}


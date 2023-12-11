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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;

public class SearchButtonHandler implements ActionListener {
    private final SearchWindow searchWindow;

    /**
     * The search Window must be passed since This event handler needs to know
     * which File type is selected. <b>It asks the window... </b>.. Not to
     * mention the search string
     */
    public SearchButtonHandler(final SearchWindow searchWindow, final JButton searchButton) {
        this.searchWindow = searchWindow;
    }

    /**
     * Starts / stops the search. This code is called by the text field and
     * search button.
     */
    public void actionPerformed(final ActionEvent event) {
        final JButton searchButton = (JButton) event.getSource();

        if (!searchButton.isEnabled())
            return;

        if (searchButton.getLabel().equals("Stop")) {
            searchWindow.stopSearch();
        } else {
            searchWindow.startSearch();
        }
    }
}


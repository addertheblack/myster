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

import com.myster.search.SearchEngine;
import com.myster.util.MysterThread;

public class SearchButtonEvent implements ActionListener {
    SearchWindow a;

    boolean flag = false;

    SearchEngine sengine;

    Button button;

    ButtonLabelThread bl;

    /**
     * The search Window must be passed since This event handler needs to know
     * which File type is selected. <b>It asks the window... </b>.. Not to
     * mention the search string
     * 
     * Note: At this writting this has not been implemented.. ie: The Handler
     * doens't recognized types.
     */
    public SearchButtonEvent(SearchWindow a, Button b) {
        this.a = a;
        sengine = new SearchEngine(a);
        button = b;
        bl = new ButtonLabelThread();
    }

    /**
     * Handles resquest for action, action, action, at the sringfield speedway,
     * speedway, speedway. This routines needs to be redone.. the code is not
     * pretty enough. Don't I deserve pretty code??? This routine starts up n
     * new search thread where n is the number of active ips in the ip list. IF
     * the are no iactive ips in the ip list, Myster goes to the array of last
     * resort for some possible active IPs to add. This saves the user from
     * adding ips manually at the start.
     */
    public synchronized void actionPerformed(ActionEvent event) {
        if (!button.isEnabled())
            return; //if the button is not enabled then don't do anything
        //this line is here on the slim chance the user can fire off two click
        // events in a row, one
        //of which gets stuck on the object's lock (this routine is
        // synchronized).
        //This ine should never come into play but better safe then sorry.

        if (flag) {
            button.setEnabled(false);
            bl.end();
            button.setLabel("Stop");
            (new KillerThread()).start();
        } else {
            try {
                sengine.end();
            } catch (Exception ex) {
            }

            sengine = new SearchEngine(a);
            sengine.start();
            a.setTitle("Search For \""+a.getSearchString()+"\"");
            bl = new ButtonLabelThread();
            button.setLabel("Stop");
            flag = true;
            bl.start();
        }
    }

    private class KillerThread extends MysterThread {
        public void run() {
            try {
                sengine.end();
            } catch (Exception ex) {
            }

            button.setLabel("Search");
            flag = false;
            button.setEnabled(true);
            a.say("Search Stopped");
        }
    }

    private class ButtonLabelThread extends MysterThread {

        public void run() {
            try {
                sengine.join();
            } catch (InterruptedException ex) {
                return;
            }
            button.setLabel("Search");
            flag = false;
        }

        public void end() {
            interrupt();
            try {
                join();
            } catch (Exception ex) {
            }
        }
    }
}


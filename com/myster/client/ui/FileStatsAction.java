/*
 * 
 * Title: Myster Open Source Author: Andrew Trumper Description: Generic Myster
 * Code
 * 
 * This code is under GPL
 * 
 * Copyright Andrew Trumper 2000-2001
 */

package com.myster.client.ui;

import com.general.mclist.MCListEvent;
import com.general.mclist.MCListEventAdapter;
import com.myster.util.MysterThread;

public class FileStatsAction extends MCListEventAdapter {
    ClientWindow w;

    MysterThread t;

    public FileStatsAction(ClientWindow w) {
        this.w = w;
    }

    public void selectItem(MCListEvent e) {
        try {
            t.end();
        } catch (Exception ex) {
        }
        w.clearFileStats();
        t = (new FileInfoListerThread(w));
        t.start();
    }

    public void unselectItem(MCListEvent e) {
        try {
            t.end();
        } catch (Exception ex) {
        }
        w.clearFileStats();
    }
}
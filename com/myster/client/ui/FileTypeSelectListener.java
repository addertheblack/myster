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

public class FileTypeSelectListener extends MCListEventAdapter {
    ClientWindow w;

    MysterThread t;

    public FileTypeSelectListener(ClientWindow w) {
        this.w = w;
    }

    public void selectItem(MCListEvent e) {
        w.clearFileList();
        t = (new FileListerThread(w));//a, w, w.getCurrentIP(),
                                      // w.getCurrentType()));
        t.start();
    }

    public void unselectItem(MCListEvent e) {
        try {
            t.end();
        } catch (Exception ex) {
        }
        w.clearFileList();
    }
}
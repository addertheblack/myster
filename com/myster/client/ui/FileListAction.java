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
import com.myster.net.MysterAddress;
import com.myster.search.MysterFileStub;

public class FileListAction extends MCListEventAdapter {
    ClientWindow w;

    public FileListAction(ClientWindow w) {
        this.w = w;
    }

    public void doubleClick(MCListEvent e) {
        try {
            MysterFileStub stub = new MysterFileStub(new MysterAddress(w
                    .getCurrentIP()), w.getCurrentType(), w.getCurrentFile());
            com.myster.client.stream.StandardSuite.downloadFile(stub
                    .getMysterAddress(), stub);
        } catch (java.io.IOException ex) {
            com.general.util.AnswerDialog.simpleAlert(w,
                    "Could not connect to server.");
        }
    }

}
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
import com.myster.client.net.MysterProtocol;
import com.myster.net.MysterAddress;
import com.myster.search.HashCrawlerManager;
import com.myster.search.MysterFileStub;
import com.myster.ui.MysterFrameContext;

public class FileListAction extends MCListEventAdapter {
    private final HashCrawlerManager hashManager;
    private final MysterProtocol protocol;
    private final ClientWindow w;
    private final MysterFrameContext mysterFrameContext;

    public FileListAction(MysterProtocol protocol, HashCrawlerManager hashManager, MysterFrameContext mysterFrameContext, ClientWindow w) {
        this.hashManager = hashManager;
        this.protocol = protocol;
        this.mysterFrameContext = mysterFrameContext;
        this.w = w;
    }

    public void doubleClick(MCListEvent e) {
        try {
            MysterFileStub stub =
                    new MysterFileStub(MysterAddress.createMysterAddress(w.getCurrentIP()),
                                       w.getCurrentType(),
                                       w.getCurrentFile());
            protocol.getStream()
                    .downloadFile(mysterFrameContext, hashManager, stub.getMysterAddress(), stub);
        } catch (java.io.IOException ex) {
            com.general.util.AnswerDialog.simpleAlert(w, "Could not connect to server.");
        }
    }

}
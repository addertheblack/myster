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

import java.nio.file.Path;
import java.util.Optional;

import com.general.mclist.JMCList;
import com.general.mclist.MCListEvent;
import com.general.mclist.MCListEventAdapter;
import com.general.mclist.TreeMCListTableModel;
import com.general.mclist.TreeMCListTableModel.TreeMCListItem;
import com.myster.net.MysterAddress;
import com.myster.net.client.MysterProtocol;
import com.myster.net.stream.client.msdownload.MSDownloadParams;
import com.myster.search.HashCrawlerManager;
import com.myster.search.MysterFileStub;
import com.myster.ui.DownloadStartErrorDialog;
import com.myster.ui.MysterFrameContext;
import com.myster.ui.DownloadDirectoryChooser;

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
            if (w.isDir()) {
                JMCList parent = (JMCList) e.getParent();

                TreeMCListItem<?> mcListItem =
                        (TreeMCListItem<?>) parent.getMCListItem(parent.getSelectedIndex());

                mcListItem.setOpen(!mcListItem.isOpen());
                ((TreeMCListTableModel) parent.getModel()).resortAndRebuild();
                return;
            }

            MysterFileStub stub =
                    new MysterFileStub(MysterAddress.createMysterAddress(w.getCurrentIP()),
                                       w.getCurrentType(),
                                       w.getCurrentFile());

            Optional<Path> baseDir = resolveDownloadDirectory(stub);
            if (baseDir.isEmpty()) {
                return;
            }

            protocol.getStream()
                    .downloadFile(new MSDownloadParams(mysterFrameContext,
                                                       hashManager,
                                                       stub,
                                                       baseDir.get(),
                                                       Path.of(""),
                                                       DownloadStartErrorDialog.handler(w)));
        } catch (java.io.IOException _) {
            com.general.util.AnswerDialog.simpleAlert(w, "Could not connect to server.");
        }
    }

    private Optional<Path> resolveDownloadDirectory(MysterFileStub stub) {
        return DownloadDirectoryChooser
                .chooseWritableDownloadDirectory(w,
                                                 "Select a folder to save the file in",
                                                 false,
                                                 mysterFrameContext.fileManager(),
                                                 stub.getType());
    }

}

package com.myster.progress.ui;

import java.io.File;
import java.io.IOException;

import com.general.thread.Cancellable;
import com.general.util.Util;
import com.myster.hash.FileHash;
import com.myster.net.stream.client.msdownload.DownloadInitiator.DownloadInitiatorListener;
import com.myster.net.stream.client.msdownload.MSDownloadListener;
import com.myster.net.stream.client.msdownload.MSPartialFile;
import com.myster.search.MysterFileStub;

/**
 * A wrapper around DownloadInitiatorListener that ensures all method calls
 * are executed on the Swing EDT thread.
 * 
 * This is necessary because download operations happen on background threads,
 * but UI updates must happen on the EDT.
 */
public class EdtDownloadInitiatorListener implements DownloadInitiatorListener {
    private final DownloadInitiatorListener impl;
    
    public EdtDownloadInitiatorListener(DownloadInitiatorListener impl) {
        this.impl = impl;
    }

    @Override
    public void setCancellable(Cancellable cancellable) {
        Util.invokeLater(() -> {
            impl.setCancellable(cancellable);
        });
    }

    @Override
    public void setTitle(String title) {
        Util.invokeLater(() -> {
            impl.setTitle(title);
        });
    }

    @Override
    public void setText(String text) {
        Util.invokeLater(() -> {
            impl.setText(text);
        });
    }

    @Override
    public MSDownloadListener getMsDownloadListener() {
        return Util.callAndWaitNoThrows(() -> {
            return impl.getMsDownloadListener();
        });
    }

    @Override
    public File getFileToDownloadTo(MysterFileStub stub) {
        return Util.callAndWaitNoThrows(() -> {
            return impl.getFileToDownloadTo(stub);
        });
    }

    @Override
    public MSPartialFile createMSPartialFile(MysterFileStub stub,
                                             File fileToDownloadTo,
                                             long estimatedFileLength,
                                             FileHash[] hashes)
            throws IOException {
        return Util.callAndWaitNoThrows(() -> {
            return impl.createMSPartialFile(stub,
                                           fileToDownloadTo,
                                           estimatedFileLength,
                                           hashes);
        });
    }

    @Override
    public void moveFileToFinalDestination(File sourceFile) {
        Util.invokeLater(() -> {
            impl.moveFileToFinalDestination(sourceFile);
        });
    }
}

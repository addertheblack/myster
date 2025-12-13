package com.myster.progress.ui;

import java.io.File;
import java.io.IOException;

import com.general.thread.Cancellable;
import com.general.util.AnswerDialog;
import com.myster.hash.FileHash;
import com.myster.net.stream.client.msdownload.DownloadInitiator.DownloadInitiatorListener;
import com.myster.net.stream.client.msdownload.ObsoleteHandler;
import com.myster.net.stream.client.msdownload.MSDownloadParams;
import com.myster.net.stream.client.msdownload.MSPartialFile;
import com.myster.net.stream.client.msdownload.MultiSourceDownload;
import com.myster.net.stream.client.msdownload.MultiSourceEvent;
import com.myster.net.stream.client.msdownload.MultiSourceUtilities;
import com.myster.search.MysterFileStub;
import com.myster.ui.MysterFrameContext;

public class EdtFileProgressWindow implements DownloadInitiatorListener {
    private final FileProgressWindow progress;
    private final MSDownloadParams params;
    
    private Cancellable cancellable;
    private boolean done;
    
    public EdtFileProgressWindow(MysterFrameContext context, MSDownloadParams params) {
        this.params = params;
        
        progress = new com.myster.progress.ui.FileProgressWindow(context, "Connecting..");
    }
    
    @Override
    public void setCancellable(Cancellable c) {
        cancellable = c;
        
        progress.addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent e) {
                if (!done && !MultiSourceUtilities.confirmCancel(progress)) {
                    return;
                }

                cancellable.cancel();

                progress.setVisible(false);
            }
        });
        
        progress.show();
    }

    @Override
    public void setTitle(String title) {
        progress.setTitle(title);
    }

    @Override
    public void setText(String text) {
        progress.setText(text);
    }


    @Override
    public ObsoleteHandler getMsDownloadListener() {
        return new ObsoleteHandler(progress, cancellable) {
            @Override
            public void doneDownload(MultiSourceEvent event) {
                done = true;
                super.doneDownload(event);
            }  
        };
    }

    @Override
    public File getFileToDownloadTo(MysterFileStub stub) {
        return MultiSourceUtilities.getFileToDownloadTo(stub.name(),
                                                        progress,
                                                        params.targetDir(),
                                                        params.subDirectory());
    }

    @Override
    public MSPartialFile createMSPartialFile(MysterFileStub stub,
                                             File fileToDownloadTo,
                                             long estimatedFileLength,
                                             FileHash[] hashes) throws IOException {
        try {
            return MSPartialFile.create(stub.getMysterAddress(),
                                        stub.getName(),
                                        new File(fileToDownloadTo.getParent()),
                                        stub.getType(),
                                        MultiSourceDownload.DEFAULT_CHUNK_SIZE,
                                        hashes,
                                        estimatedFileLength);
        } catch (IOException ex) {
            AnswerDialog
                    .simpleAlert(progress,
                                 "I can't create a partial file because of: \n\n"
                                         + ex.getMessage()
                                         + "\n\nIf I can't make this partial file I can't use multi-source download.");
            throw ex;
        }
    }

    @Override
    public void moveFileToFinalDestination(File sourceFile) {
        MultiSourceUtilities.moveFileToFinalDestination(sourceFile, s -> AnswerDialog.simpleAlert(progress, s));
    }
}

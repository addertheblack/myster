package com.myster.progress.ui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import com.general.thread.Cancellable;
import com.general.util.AnswerDialog;
import com.myster.hash.FileHash;
import com.myster.net.stream.client.msdownload.DownloadStartException;
import com.myster.net.stream.client.msdownload.DownloadInitiator.DownloadInitiatorListener;
import com.myster.net.stream.client.msdownload.ExistingDownloadTargetHandler;
import com.myster.net.stream.client.msdownload.ObsoleteHandler;
import com.myster.net.stream.client.msdownload.MSDownloadParams;
import com.myster.net.stream.client.msdownload.MSPartialFile;
import com.myster.net.stream.client.msdownload.MultiSourceDownload;
import com.myster.net.stream.client.msdownload.MultiSourceEvent;
import com.myster.net.stream.client.msdownload.MultiSourceUtils;
import com.myster.search.MysterFileStub;
import com.myster.ui.MysterFrameContext;

public class EdtFileProgressWindow implements DownloadInitiatorListener {
    private static final String STOP_DOWNLOAD = "Kill";

    private static final String CANCEL = "Don't Kill";

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
                if (!done && !confirmCancel()) {
                    return;
                }

                cancellable.cancel();

                progress.setVisible(false);
            }
        });
        
        progress.show();
    }

    private boolean confirmCancel() {
        final String choice = AnswerDialog.simpleAlert(progress,
                "Are you sure you want to kill this download?", new String[] { STOP_DOWNLOAD,
                        CANCEL });
        return choice.equals(STOP_DOWNLOAD);
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
    public File getFileToDownloadTo(MysterFileStub stub) throws DownloadStartException {
        return MultiSourceUtils.getFileToDownloadTo(stub.name(),
                                                    params.targetDir(),
                                                    params.subDirectory(),
                                                    this::chooseForExistingTarget);
    }

    private ExistingDownloadTargetHandler.Decision chooseForExistingTarget(Path partialFile,
                                                                          Path finalFile) {
        String answer = AnswerDialog.simpleAlert(progress,
                "A file by the name of " + partialFile.getFileName()
                        + " already exists. What do you want to do?",
                new String[] { "Write-Over", "Cancel" });
        return "Write-Over".equals(answer) ? ExistingDownloadTargetHandler.Decision.OVERWRITE
                : ExistingDownloadTargetHandler.Decision.CANCEL;
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
        MultiSourceUtils.moveFileToFinalDestination(sourceFile, s -> AnswerDialog.simpleAlert(progress, s));
    }
}

package com.myster.progress.ui;

import java.io.File;
import java.io.IOException;

import com.general.thread.Cancellable;
import com.general.util.AnswerDialog;
import com.myster.hash.FileHash;
import com.myster.net.stream.client.msdownload.DownloadInitiator.DownloadInitiatorListener;
import com.myster.net.stream.client.msdownload.MSDownloadListener;
import com.myster.net.stream.client.msdownload.MSDownloadParams;
import com.myster.net.stream.client.msdownload.MSPartialFile;
import com.myster.net.stream.client.msdownload.MultiSourceDownload;
import com.myster.net.stream.client.msdownload.MultiSourceUtilities;
import com.myster.progress.ui.ProgressBannerManager.Banner;
import com.myster.search.MysterFileStub;

/**
 * Manages a single download within the ProgressManagerWindow.
 * This class implements DownloadInitiatorListener and creates nested listeners
 * for the overall download and individual segment downloaders (connections).
 */
public class ProgressManagerDownloadListener implements DownloadInitiatorListener {
    private final ProgressManagerWindow window;
    private final MSDownloadParams params;
    
    private ProgressManagerWindow.DownloadMCListItem downloadItem;
    
    public interface AddBanners {
        void addNewBannerToQueue(Banner b);
    }

    public ProgressManagerDownloadListener(ProgressManagerWindow window,
                                           MSDownloadParams params) {
        this.window = window;
        this.params = params;

        var rootPath = new com.general.mclist.TreeMCListTableModel.TreePathString(new String[] {});
        this.downloadItem = new ProgressManagerWindow.DownloadMCListItem(params.stub().getName(),
                                                                         rootPath,
                                                                         null,
                                                                         true);
    }

    @Override
    public void setCancellable(Cancellable c) {
        this.downloadItem.setCancellable(c);
        
        // Show the window if it's not already visible
        if (!window.isVisible()) {
            window.setVisible(true);
        }
    }

    @Override
    public void setTitle(String title) {
        // Main download title - could update window title if this is the only download
        // For now, we'll just use it when creating the download item
        
        downloadItem.getObject().setStatus(title);
    }

    @Override
    public void setText(String text) {
        downloadItem.getObject().setStatus(text);
        window.getDownloadList().repaint();
    }

    @Override
    public MSDownloadListener getMsDownloadListener() {
        return new ProgManDownloadHandler(window, downloadItem, params.stub().getName());
    }

    @Override
    public File getFileToDownloadTo(MysterFileStub stub) {
        return MultiSourceUtilities.getFileToDownloadTo(stub.name(),
                                                        window,
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
            AnswerDialog.simpleAlert(window,
                                    "I can't create a partial file because of: \n\n"
                                            + ex.getMessage()
                                            + "\n\nIf I can't make this partial file I can't use multi-source download.");
            throw ex;
        }
    }

    @Override
    public void moveFileToFinalDestination(File sourceFile) {
        MultiSourceUtilities.moveFileToFinalDestination(sourceFile, 
                                                        s -> AnswerDialog.simpleAlert(window, s));
    }
    
}

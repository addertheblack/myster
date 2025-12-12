
package com.myster.net.stream.client.msdownload;

import static com.myster.net.stream.client.msdownload.MultiSourceDownload.toIoFile;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.logging.Logger;

import com.general.thread.Cancellable;
import com.myster.hash.FileHash;
import com.myster.mml.MessagePak;
import com.myster.net.MysterSocket;
import com.myster.net.stream.client.MysterSocketFactory;
import com.myster.net.stream.client.StandardSuiteStream;
import com.myster.net.stream.client.msdownload.MultiSourceDownload.FileMover;
import com.myster.search.HashCrawlerManager;
import com.myster.search.MysterFileStub;
import com.myster.ui.MysterFrameContext;

public class DownloadInitiator implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(DownloadInitiator.class.getName());

    private final MysterFileStub stub;
    private final HashCrawlerManager crawlerManager;
    private final MysterFrameContext context;
    private final MSDownloadParams params;

    public DownloadInitiator(MSDownloadParams p) {
        this.context = p.context();
        this.stub = p.stub();
        this.crawlerManager = p.crawlerManager();
        this.params = p;
    }

    /**
     * This is used while the download is still in the process of starting
     */
    public interface DownloadInitiatorListener extends FileMover {
        // First step
        void setCancellable(Cancellable cancellable);

        // Can be called at any time after setCancellable();
        void setTitle(String title);

        void setText(String title);
        
        MSDownloadListener getMsDownloadListener();

        File getFileToDownloadTo(MysterFileStub stub);

        MSPartialFile createMSPartialFile(MysterFileStub stub,
                                          File fileToDownloadTo,
                                          long estimatedFileLength,
                                          FileHash[] hashes)
                throws IOException;
    }


    public void run() {
        final DownloadInitiatorListener progress = context.downloadManager().bindToFileProgressGui(params);

        progress.setCancellable(this::cancel);
        
        progress.setTitle("Downloading " + stub.getName());
        progress.setText("Starting...");
        
        MysterSocket socket = null;
        try {
            socket = MysterSocketFactory.makeStreamConnection(stub.getMysterAddress());
        } catch (Exception ex) {
            LOGGER.severe("Could not connect to server: " + ex.toString());
            
            com.general.util.AnswerDialog.simpleAlert("Could not connect to server.");
            
            return;
        }

        try {
            downloadFile(socket, crawlerManager, stub, progress);
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            StandardSuiteStream.disconnectWithoutException(socket);
        }

    }

    // should not be public
    private void downloadFile(final MysterSocket socket,
                              final HashCrawlerManager crawlerManager,
                              final MysterFileStub stub,
                              final DownloadInitiatorListener progress)
            throws IOException {

        try {
            progress.setText( "Getting File Statistics...");

            if (endFlag)
                return;
            MessagePak fileStats = StandardSuiteStream.getFileStats(socket, stub);

            progress.setText("Trying to use multi-source download...");

            if (endFlag)
                return;

            final File theFile = progress.getFileToDownloadTo(stub);
            if (theFile == null) {
                progress.setText( "User cancelled...");
                return;
            }
            if (endFlag)
                return;

            if (!tryMultiSourceDownload(stub, crawlerManager, progress, fileStats, theFile)) {
                throw new IOException("MultiSourceDownload failed");
            }
        } catch (IOException ex) {
            ex.printStackTrace();

            progress.setText("Could not download file...");
        }
    }

    @SuppressWarnings("resource")
    private boolean tryMultiSourceDownload(final MysterFileStub stub,
                                           HashCrawlerManager crawlerManager,
                                           final DownloadInitiatorListener downloadInitListener,
                                           MessagePak fileStats,
                                           final File theFile)
            throws IOException {
        FileHash hash = MultiSourceUtilities.getHashFromStats(fileStats);
        if (hash == null)
            return false;

        long fileLengthFromStats = MultiSourceUtilities.getLengthFromStats(fileStats);
        MSPartialFile partialFile = downloadInitListener
                .createMSPartialFile(stub, theFile, fileLengthFromStats, new FileHash[] { hash });

        msDownload = new MultiSourceDownload(toIoFile(new RandomAccessFile(theFile, "rw"), theFile),
                                             crawlerManager,
                                             downloadInitListener.getMsDownloadListener(),
                                             downloadInitListener,
                                             partialFile);
        msDownload.setInitialServers(new MysterFileStub[] { stub });

        synchronized (this) {
            if (!endFlag) {
                msDownload.start();
            }
        }

        return true;
    }

    private MultiSourceDownload msDownload;
    private boolean endFlag;

    public synchronized void cancel() {
        endFlag = true;

        if (msDownload != null)
            msDownload.cancel();
    }
}
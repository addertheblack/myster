
package com.myster.net.stream.client.msdownload;

import static com.myster.net.stream.client.msdownload.MultiSourceDownload.toIoFile;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.Semaphore;
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
    private static final Logger log = Logger.getLogger(DownloadInitiator.class.getName());
    private static final Semaphore connectionSem = new Semaphore(5);

    private final MysterFileStub stub;
    private final HashCrawlerManager crawlerManager;
    private final MysterFrameContext context;
    private final MSDownloadParams params;
    private final MSDownloadLocalQueue downloadQueue;


    public DownloadInitiator(MSDownloadParams p, MSDownloadLocalQueue downloadQueue) {
        this.downloadQueue = downloadQueue;
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

        File getFileToDownloadTo(MysterFileStub stub) throws DownloadStartException;

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
            connectionSem.acquire();
            try {
                socket = MysterSocketFactory.makeStreamConnection(stub.getMysterAddress());
            } finally {
                connectionSem.release();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.severe("Thread interrupted while waiting for connection permit: " + ex.toString());
            reportStartFailure(progress, new DownloadInterruptedException(ex));
            
            return;
        } catch (Exception ex) {
            ex.printStackTrace();
            log.severe("Could not connect to server: " + ex.toString());
            reportStartFailure(progress,
                               new DownloadServerConnectionException(stub.getMysterAddress()
                                       .toString(), ex));
            
            return;
        }

        try {
            downloadFile(socket, crawlerManager, stub, progress);
        } catch (DownloadStartException ex) {
            reportStartFailure(progress, ex);
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
            throws IOException, DownloadStartException {

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
        } catch (DownloadStartException ex) {
            throw ex;
        } catch (IOException ex) {
            ex.printStackTrace();
            throw new DownloadServerConnectionException(stub.getMysterAddress().toString(), ex);
        }
    }

    private void reportStartFailure(DownloadInitiatorListener progress,
                                    DownloadStartException exception) {
        progress.setText(exception.getMessage());
        try {
            params.reportStartFailure(exception);
        } catch (RuntimeException callbackException) {
            log.warning("Download start failure handler failed: " + callbackException);
        }
    }

    @SuppressWarnings("resource")
    private boolean tryMultiSourceDownload(final MysterFileStub stub,
                                           HashCrawlerManager crawlerManager,
                                           final DownloadInitiatorListener downloadInitListener,
                                           MessagePak fileStats,
                                           final File theFile)
            throws IOException {
        FileHash hash = MultiSourceUtils.getHashFromStats(fileStats);
        if (hash == null)
            return false;

        long fileLengthFromStats = MultiSourceUtils.getLengthFromStats(fileStats);
        MSPartialFile partialFile = downloadInitListener
                .createMSPartialFile(stub, theFile, fileLengthFromStats, new FileHash[] { hash });

        msDownload = new MultiSourceDownload(toIoFile(new RandomAccessFile(theFile, "rw"), theFile),
                                             crawlerManager,
                                             downloadInitListener.getMsDownloadListener(),
                                             downloadInitListener,
                                             partialFile,
                                             downloadQueue);
        msDownload.addInitialServers(new MysterFileStub[] { stub });

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


package com.myster.client.stream.msdownload;

import static com.myster.client.stream.msdownload.MultiSourceDownload.toIoFile;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

import com.general.thread.Cancellable;
import com.general.util.AnswerDialog;
import com.general.util.Util;
import com.myster.client.stream.StandardSuite;
import com.myster.client.stream.msdownload.MultiSourceDownload.FileMover;
import com.myster.hash.FileHash;
import com.myster.mml.RobustMML;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.net.MysterSocketFactory;
import com.myster.search.HashCrawlerManager;
import com.myster.search.MysterFileStub;
import com.myster.ui.MysterFrameContext;
import com.myster.util.FileProgressWindow;

public class DownloadInitiator implements Runnable {
    private final MysterAddress ip;
    private final MysterFileStub stub;
    private final HashCrawlerManager crawlerManager;
    private final MysterFrameContext context;

    public DownloadInitiator(MysterFrameContext c,
                             HashCrawlerManager crawlerManager,
                             MysterAddress ip,
                             MysterFileStub stub) {
        this.context = c;
        this.ip = ip;
        this.stub = stub;
        this.crawlerManager = crawlerManager;
    }

    interface MsDownloadListener extends FileMover {
        // First step
        void setCancellable(Cancellable cancellable);

        // Can be called at any time after setCancellable();
        void setTitle(String title);

        void setText(String title);
        
        void setDone();
        
        MSDownloadHandler getMSDownloadHandler();

        File getFileToDownloadTo(MysterFileStub stub);

        MSPartialFile createMSPartialFile(MysterFileStub stub,
                                          File fileToDownloadTo,
                                          long estimatedFileLength,
                                          FileHash[] hashes)
                throws IOException;
    }

    private static MsDownloadListener bindToFileProgressWindow(MysterFrameContext context) {
        return new MsDownloadListener() {
            EdtFileProgressWindow w = null;
            
            private void init() {
                if (w == null) {
                    w = new EdtFileProgressWindow(context);
                }
            }
            
            @Override
            public void setCancellable(Cancellable cancellable) {
                Util.invokeLater(()-> {
                    init();
                    
                    w.setCancellable(cancellable);
                });
            }

            @Override
            public void setTitle(String title) {
                Util.invokeLater(()-> {
                    init();
                    
                    w.setTitle(title);
                });
            }

            @Override
            public void setText(String text) {
                Util.invokeLater(()-> {
                    init();
                    
                    w.setText(text);
                });
            }
            
            @Override
            public void setDone() {
                Util.invokeLater(()-> {
                    init();
                    
                    w.setDone();
                });
            }
            
            @Override
            public MSDownloadHandler getMSDownloadHandler() {
                return Util.callAndWaitNoThrows(() -> {
                    init();

                    return w.getMSDownloadHandler();
                });
            }

            @Override
            public File getFileToDownloadTo(MysterFileStub stub) {
                return Util.callAndWaitNoThrows(()-> {
                    init();
                    
                    return w.getFileToDownloadTo(stub);
                });
            }

            @Override
            public MSPartialFile createMSPartialFile(MysterFileStub stub,
                                                     File fileToDownloadTo,
                                                     long estimatedFileLength,
                                                     FileHash[] hashes)
                    throws IOException {
                return Util.callAndWaitNoThrows(() -> {
                    init();

                    return w.createMSPartialFile(stub,
                                                 fileToDownloadTo,
                                                 estimatedFileLength,
                                                 hashes);
                });
            }

            @Override
            public void moveFileToFinalDestination(File sourceFile) {
                Util.invokeLater(() -> {
                    init();

                    w.moveFileToFinalDestination(sourceFile);
                });
            }
        };
    }
    
    private static class EdtFileProgressWindow implements MsDownloadListener {
        private final FileProgressWindow progress;
        
        private Cancellable cancellable;
        private boolean done;
        
        public EdtFileProgressWindow(MysterFrameContext context) {
            progress = new com.myster.util.FileProgressWindow(context, "Connecting..");
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
        public void setDone() {
            done = true;
        }

        @Override
        public MSDownloadHandler getMSDownloadHandler() {
            return new MSDownloadHandler(progress);
        }

        @Override
        public File getFileToDownloadTo(MysterFileStub stub) {
            return MultiSourceUtilities.getFileToDownloadTo(stub, progress);
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
            MultiSourceUtilities.moveFileToFinalDestination(sourceFile, progress);
        }
    }

    public void run() {
        final MsDownloadListener progress = bindToFileProgressWindow(context);

        progress.setCancellable(this::cancel);
        
        progress.setTitle("Downloading " + stub.getName());
        progress.setText("Starting...");
        
        MysterSocket socket = null;
        try {
            socket = MysterSocketFactory.makeStreamConnection(ip);
        } catch (Exception ex) {
            com.general.util.AnswerDialog.simpleAlert("Could not connect to server.");
            return;
        }

        try {
            downloadFile(socket, crawlerManager, stub, progress);
        } catch (IOException ex) {
            // ..
        } finally {
            StandardSuite.disconnectWithoutException(socket);
        }

    }

    // should not be public
    private void downloadFile(final MysterSocket socket,
                              final HashCrawlerManager crawlerManager,
                              final MysterFileStub stub,
                              final MsDownloadListener progress)
            throws IOException {

        try {
            progress.setText( "Getting File Statistics...");

            if (endFlag)
                return;
            RobustMML mml = StandardSuite.getFileStats(socket, stub);

            progress.setText("Trying to use multi-source download...");

            final boolean DONT_USE_MULTISOURCE = false;
            if (DONT_USE_MULTISOURCE)
                throw new IOException("Toss and catch: Multisource download disabled");

            if (endFlag)
                return;

            final File theFile = progress.getFileToDownloadTo(stub);
            if (theFile == null) {
                progress.setText( "User cancelled...");
                return;
            }
            if (endFlag)
                return;

            if (!tryMultiSourceDownload(stub, crawlerManager, progress, mml, theFile)) {
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
                                           final MsDownloadListener msDownloadListener,
                                           RobustMML mml,
                                           final File theFile)
            throws IOException {
        FileHash hash = MultiSourceUtilities.getHashFromStats(mml);
        if (hash == null)
            return false;

        long fileLengthFromStats = MultiSourceUtilities.getLengthFromStats(mml);
        MSPartialFile partialFile = msDownloadListener
                .createMSPartialFile(stub, theFile, fileLengthFromStats, new FileHash[] { hash });

        msDownload = new MultiSourceDownload(toIoFile(new RandomAccessFile(theFile, "rw"), theFile),
                                             crawlerManager,
                                             msDownloadListener.getMSDownloadHandler(),
                                             msDownloadListener,
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
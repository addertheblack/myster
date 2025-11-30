
package com.myster.net.stream.client.msdownload;

import static com.myster.net.stream.client.msdownload.MultiSourceDownload.toIoFile;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

import com.general.thread.Cancellable;
import com.general.util.AnswerDialog;
import com.general.util.Util;
import com.myster.hash.FileHash;
import com.myster.mml.MessagePak;
import com.myster.net.MysterSocket;
import com.myster.net.stream.client.MysterSocketFactory;
import com.myster.net.stream.client.StandardSuiteStream;
import com.myster.net.stream.client.msdownload.MultiSourceDownload.FileMover;
import com.myster.search.HashCrawlerManager;
import com.myster.search.MysterFileStub;
import com.myster.ui.MysterFrameContext;
import com.myster.util.FileProgressWindow;

public class DownloadInitiator implements Runnable {
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
    interface DownloadInitiatorListener extends FileMover {
        // First step
        void setCancellable(Cancellable cancellable);

        // Can be called at any time after setCancellable();
        void setTitle(String title);

        void setText(String title);
        
        MSDownloadHandler getMsDownloadHandler();

        File getFileToDownloadTo(MysterFileStub stub);

        MSPartialFile createMSPartialFile(MysterFileStub stub,
                                          File fileToDownloadTo,
                                          long estimatedFileLength,
                                          FileHash[] hashes)
                throws IOException;
    }

    private static DownloadInitiatorListener bindToFileProgressWindow(MysterFrameContext context, MSDownloadParams params) {
        return new DownloadInitiatorListener() {
            EdtFileProgressWindow w = null;
            
            private void init() {
                if (w == null) {
                    w = new EdtFileProgressWindow(context, params);
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
            public MSDownloadHandler getMsDownloadHandler() {
                return Util.callAndWaitNoThrows(() -> {
                    init();

                    return w.getMsDownloadHandler();
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
    
    private static class EdtFileProgressWindow implements DownloadInitiatorListener {
        private final FileProgressWindow progress;
        private final MSDownloadParams params;
        
        private Cancellable cancellable;
        private boolean done;
        
        public EdtFileProgressWindow(MysterFrameContext context, MSDownloadParams params) {
            this.params = params;
            
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
        public MSDownloadHandler getMsDownloadHandler() {
            return new MSDownloadHandler(progress) {
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

    public void run() {
        final DownloadInitiatorListener progress = bindToFileProgressWindow(context, params);

        progress.setCancellable(this::cancel);
        
        progress.setTitle("Downloading " + stub.getName());
        progress.setText("Starting...");
        
        MysterSocket socket = null;
        try {
            socket = MysterSocketFactory.makeStreamConnection(stub.getMysterAddress());
        } catch (Exception _) {
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
                                             downloadInitListener.getMsDownloadHandler(),
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
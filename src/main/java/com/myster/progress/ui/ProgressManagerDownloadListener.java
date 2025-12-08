package com.myster.progress.ui;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.general.thread.Cancellable;
import com.general.util.AnswerDialog;
import com.general.util.Util;
import com.myster.hash.FileHash;
import com.myster.net.stream.client.msdownload.DownloadInitiator.DownloadInitiatorListener;
import com.myster.net.stream.client.msdownload.MSDownloadListener;
import com.myster.net.stream.client.msdownload.MSDownloadParams;
import com.myster.net.stream.client.msdownload.MSPartialFile;
import com.myster.net.stream.client.msdownload.MSSegmentEvent;
import com.myster.net.stream.client.msdownload.MultiSourceDownload;
import com.myster.net.stream.client.msdownload.MultiSourceEvent;
import com.myster.net.stream.client.msdownload.MultiSourceUtilities;
import com.myster.net.stream.client.msdownload.SegmentDownloader;
import com.myster.net.stream.client.msdownload.SegmentDownloaderEvent;
import com.myster.net.stream.client.msdownload.SegmentDownloaderListener;
import com.myster.net.stream.client.msdownload.SegmentMetaDataEvent;
import com.myster.progress.ui.ProgressBannerManager.Banner;
import com.myster.search.MysterFileStub;
import com.myster.ui.MysterFrameContext;

/**
 * Manages a single download within the ProgressManagerWindow.
 * This class implements DownloadInitiatorListener and creates nested listeners
 * for the overall download and individual segment downloaders (connections).
 */
public class ProgressManagerDownloadListener implements DownloadInitiatorListener {
    private final ProgressManagerWindow window;
    private final MSDownloadParams params;
    private final MysterFrameContext context;
    
    private ProgressManagerWindow.DownloadMCListItem downloadItem;
    private Cancellable cancellable; // TBD
    
    public interface AddBanners {
        void addNewBannerToQueue(Banner b);
    }
    
    public ProgressManagerDownloadListener(ProgressManagerWindow window, 
                                          MysterFrameContext context,
                                          MSDownloadParams params) {
        this.window = window;
        this.context = context;
        this.params = params;
    }

    @Override
    public void setCancellable(Cancellable c) {
        this.cancellable = c;
        
        // Show the window if it's not already visible
        if (!window.isVisible()) {
            window.setVisible(true);
        }
    }

    @Override
    public void setTitle(String title) {
        // Main download title - could update window title if this is the only download
        // For now, we'll just use it when creating the download item
    }

    @Override
    public void setText(String text) {
        if (downloadItem != null) {
            downloadItem.getObject().setStatus(text);
            window.getDownloadList().repaint();
        }
    }

    @Override
    public MSDownloadListener getMsDownloadListener() {
        return new DownloadHandler();
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
    
    /**
     * Handles overall download events and manages sub-downloads.
     */
    private class DownloadHandler implements MSDownloadListener {
        private final Map<SegmentDownloader, ConnectionHandler> connectionHandlers = new HashMap<>();
        private int connectionCounter = 0;
        private long startTime;
        
        @Override
        public void startDownload(MultiSourceEvent event) {
            startTime = System.currentTimeMillis();
            
            // Create the main download item in the tree
            var rootPath = new com.general.mclist.TreeMCListTableModel.TreePathString(new String[] {});
            downloadItem = new ProgressManagerWindow.DownloadMCListItem(
                params.stub().getName(), 
                rootPath, 
                true // is container
            );
            
            downloadItem.getObject().setTotal(event.getLength());
            downloadItem.getObject().setProgress(event.getInitialOffset());
            downloadItem.getObject().setStatus("Starting download...");
            
            window.getDownloadList().addItem(downloadItem);
        }

        @Override
        public void progress(MultiSourceEvent event) {
            if (downloadItem != null) {
                downloadItem.getObject().setProgress(event.getProgress());
                
                // Calculate overall speed
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed > 1000) {
                    long bytesDownloaded = event.getProgress() - event.getInitialOffset();
                    int speed = (int) (bytesDownloaded * 1000 / elapsed);
                    downloadItem.getObject().setSpeed(speed);
                }
                
                downloadItem.getObject().setStatus("Downloading: " + 
                    Util.getStringFromBytes(event.getProgress()));
                
                window.getDownloadList().repaint();
            }
        }

        @Override
        public void startSegmentDownloader(MSSegmentEvent event) {
            connectionCounter++;
            
            // Create a new child item for this connection
            var downloadPath = new com.general.mclist.TreeMCListTableModel.TreePathString(
                new String[] { params.stub().getName() }
            );
            
            String connectionName = "Connection " + connectionCounter; 
            var connectionItem = new ProgressManagerWindow.DownloadMCListItem(
                connectionName,
                downloadPath,
                false // not a container
            );
            
            connectionItem.getObject().setStatus("Connecting...");
            window.getDownloadList().addItem(connectionItem);
            
            // Create handler for this connection
            ConnectionHandler handler = new ConnectionHandler(connectionItem, window::addNewBannerToQueue);
            connectionHandlers.put(event.getSegmentDownloader(), handler);
            
            // Attach the listener to the segment downloader
            event.getSegmentDownloader().addListener(handler);
        }

        @Override
        public void endSegmentDownloader(MSSegmentEvent event) {
            ConnectionHandler handler = connectionHandlers.remove(event.getSegmentDownloader());
            if (handler != null) {
                handler.cleanup();
            }
        }

        @Override
        public void endDownload(MultiSourceEvent event) {
            if (downloadItem != null) {
                downloadItem.getObject().setStatus("Download stopped");
                window.getDownloadList().repaint();
            }
        }

        @Override
        public void doneDownload(MultiSourceEvent event) {
            if (downloadItem != null) {
                downloadItem.getObject().setProgress(downloadItem.getObject().getTotal());
                downloadItem.getObject().setSpeed(0);
                downloadItem.getObject().setStatus("Download complete!");
                window.getDownloadList().repaint();
            }
        }
    }
    
    /**
     * Handles events for a single connection/segment downloader.
     */
    private class ConnectionHandler implements SegmentDownloaderListener {
        private final ProgressManagerWindow.DownloadMCListItem connectionItem;
        private final AddBanners banners;
        private long segmentStartTime;
        
        // Banner metadata fields
        private byte[] image;
        private String url;
        
        public ConnectionHandler(ProgressManagerWindow.DownloadMCListItem connectionItem, 
                                AddBanners banners) {
            this.connectionItem = connectionItem;
            this.banners = banners;
        }

        @Override
        public void connected(SegmentDownloaderEvent e) {
            connectionItem.getObject().setName(e.getMysterFileStub().getMysterAddress().toString());
            connectionItem.getObject().setStatus("Negotiating...");
            window.getDownloadList().repaint();
        }

        @Override
        public void queued(SegmentDownloaderEvent e) {
            connectionItem.getObject().setStatus("Queue position: " + e.getQueuePosition());
            window.getDownloadList().repaint();
        }

        @Override
        public void startSegment(SegmentDownloaderEvent e) {
            segmentStartTime = System.currentTimeMillis();
            
            connectionItem.getObject().setTotal(e.getLength());
            connectionItem.getObject().setProgress(0);
            connectionItem.getObject().setStatus("Downloading from " + 
                e.getMysterFileStub().getMysterAddress());
            window.getDownloadList().repaint();
        }

        @Override
        public void downloadedBlock(SegmentDownloaderEvent e) {
            long progress = e.getProgress();
            connectionItem.getObject().setProgress(progress);
            
            // Calculate speed for this connection
            long elapsed = System.currentTimeMillis() - segmentStartTime;
            if (elapsed > 1000) {
                int speed = (int) (progress * 1000 / elapsed);
                connectionItem.getObject().setSpeed(speed);
            }
            
            window.getDownloadList().repaint();
        }

        @Override
        public void downloadedMetaData(SegmentMetaDataEvent e) {
            switch (e.getType()) {
            case 'i':
                flushBanner();
                image = e.getCopyOfData();
                break;
            case 'u': // URLs are UTF-8 but java's UTF decoder needs the length in the first two bytes
                byte[] temp_buffer = e.getCopyOfData();
                
                if (temp_buffer.length > (0xFFFF))
                    break; // error URL is insanely long
                
                byte[] final_buffer = new byte[temp_buffer.length + 2];
                
                final_buffer[0] = (byte) ((temp_buffer.length >> 8) & 0xFF);
                final_buffer[1] = (byte) ((temp_buffer.length) & 0xFF);
                
                for (int i = 0; i < temp_buffer.length; i++) {
                    final_buffer[i + 2] = temp_buffer[i];
                }
                
                final var in = new com.myster.net.stream.client.MysterDataInputStream(
                        new java.io.ByteArrayInputStream(final_buffer));
                
                try {
                    url = in.readUTF();
                } catch (java.io.IOException _) {
                    // nothing - means UTF was corrupt
                }
                
                flushBanner();
                break;
            default:
                // do nothing
                break;
            }
        }
        
        private void flushBanner() {
            if (image == null)
                return;
            
            banners.addNewBannerToQueue(new ProgressBannerManager.Banner(image, url));
            
            image = null;
            url = null;
        }

        @Override
        public void endSegment(SegmentDownloaderEvent e) {
            connectionItem.getObject().setProgress(connectionItem.getObject().getTotal());
            connectionItem.getObject().setStatus("Segment complete");
            window.getDownloadList().repaint();
        }

        @Override
        public void endConnection(SegmentDownloaderEvent e) {
            connectionItem.getObject().setSpeed(0);
            connectionItem.getObject().setStatus("Connection closed");
            window.getDownloadList().repaint();
        }
        
        public void cleanup() {
            // Clean up any resources if needed
        }
    }
}

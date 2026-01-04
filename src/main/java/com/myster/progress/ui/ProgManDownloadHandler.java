package com.myster.progress.ui;

import java.awt.Frame;
import java.util.HashMap;
import java.util.Map;

import com.general.thread.Cancellable;
import com.general.util.Util;
import com.myster.net.stream.client.msdownload.MSDownloadListener;
import com.myster.net.stream.client.msdownload.MSSegmentEvent;
import com.myster.net.stream.client.msdownload.MultiSourceEvent;
import com.myster.net.stream.client.msdownload.QueuedMultiSourceEvent;
import com.myster.net.stream.client.msdownload.SegmentDownloader;
import com.myster.net.stream.client.msdownload.SegmentDownloaderEvent;
import com.myster.net.stream.client.msdownload.SegmentDownloaderListener;
import com.myster.net.stream.client.msdownload.SegmentMetaDataEvent;
import com.myster.net.stream.client.msdownload.StartMultiSourceEvent;
import com.myster.progress.ui.ProgressManagerDownloadListener.AddBanners;

/**
 * Responsible for binding a download and its associated events to the download
 * ProgressManagerWindow Handles overall download events and manages
 * sub-downloads.
 */
public class ProgManDownloadHandler implements MSDownloadListener {
    private final Map<SegmentDownloader, ConnectionHandler> connectionHandlers = new HashMap<>();
    private final ProgressManagerWindow.DownloadMCListItem downloadItem;
    private final ProgressManagerWindow window;
    private final String filename;

    private int connectionCounter = 0;
    private long startTime;

    public ProgManDownloadHandler(ProgressManagerWindow window,
                           ProgressManagerWindow.DownloadMCListItem item, 
                           String filename ) {
        this.window = window;
        this.downloadItem = item;
        this.filename = filename;
    }

    public ProgManDownloadHandler(ProgressManagerWindow window,
                                  String filename,
                                  Cancellable cancellable) {
        this.window = window;
        this.filename = filename;
        
        var rootPath = new com.general.mclist.TreeMCListTableModel.TreePathString(new String[] {});
        this.downloadItem =
                new ProgressManagerWindow.DownloadMCListItem(filename, rootPath, cancellable, true);
    }
    
    
    
    @Override
    public void startDownload(StartMultiSourceEvent event) {
        startTime = System.currentTimeMillis();
        
        downloadItem.getObject().setTotal(event.getLength());
        downloadItem.getObject().setProgress(event.getInitialOffset());
        downloadItem.getObject().setStatus("Starting download...");
        downloadItem.getObject().setControl(event.getControl());
        
        window.getDownloadList().addItem(downloadItem);
    }

    @Override
    public void progress(MultiSourceEvent event) {
        downloadItem.getObject().setProgress(event.getProgress());

        // Calculate overall speed
        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed > 1000) {
            long bytesDownloaded = event.getProgress() - event.getInitialOffset();
            int speed = (int) (bytesDownloaded * 1000 / elapsed);
            downloadItem.getObject().setSpeed(speed);
        }

        downloadItem.getObject()
                .setStatus("Downloading: " + Util.getStringFromBytes(event.getProgress()));

        window.getDownloadList().repaint();
    }

    @Override
    public void startSegmentDownloader(MSSegmentEvent event) {
        connectionCounter++;
        
        // Create a new child item for this connection
        var downloadPath = new com.general.mclist.TreeMCListTableModel.TreePathString(
            new String[] { filename }
        );

        String connectionName = "Connection " + connectionCounter;
        var connectionItem = new ProgressManagerWindow.DownloadMCListItem(connectionName,
                                                                          downloadPath,
                                                                          null,
                                                                          false);

        connectionItem.getObject().setStatus("Connecting...");
        downloadItem.getObject().setStatus("Connecting to a new server...");
        
        window.getDownloadList().addItem(connectionItem);
        
        // Create handler for this connection
        ConnectionHandler handler = new ConnectionHandler(window, connectionItem, window::addNewBannerToQueue);
        connectionHandlers.put(event.getSegmentDownloader(), handler);
        
        // Attach the listener to the segment downloader
        event.getSegmentDownloader().addListener(handler);
    }

    @Override
    public void endSegmentDownloader(MSSegmentEvent event) {
        ConnectionHandler handler = connectionHandlers.remove(event.getSegmentDownloader());
        if (handler != null) {
            handler.cleanup();
            
            // Remove the connection item from the download list
            var connectionItem = handler.getConnectionItem();
            window.getDownloadList().removeItems(new com.general.mclist.MCListItemInterface[] { connectionItem });
        }
    }

    @Override
    public void pauseDownload(MultiSourceEvent event) {
        downloadItem.getObject().setStatus("Download paused");
        downloadItem.getObject().setSpeed(0);
        window.getDownloadList().repaint();
    }

    @Override
    public void resumeDownload(MultiSourceEvent event) {
        startTime = System.currentTimeMillis(); // Reset start time for speed
                                                // calculation
        downloadItem.getObject().setStatus("Resuming download...");
        window.getDownloadList().repaint();
    }

    @Override
    public void queuedDownload(QueuedMultiSourceEvent event) {
        if (event.getQueuePosition() == -1) {
            downloadItem.getObject().setStatus("Paused");
        } else {
            downloadItem.getObject().setStatus("Queued at position " + event.getQueuePosition());
        }
        downloadItem.getObject().setSpeed(0);
        window.getDownloadList().repaint();
    }

    @Override
    public void endDownload(MultiSourceEvent event) {
        downloadItem.getObject().setStatus("Download stopped");
        window.getDownloadList().repaint();
    }

    @Override
    public void doneDownload(MultiSourceEvent event) {
        downloadItem.getObject().setProgress(downloadItem.getObject().getTotal());
        downloadItem.getObject().setSpeed(0);
        downloadItem.getObject().setStatus("Download complete!");
        window.getDownloadList().repaint();
    }
    
    @Override
    public Frame getFrame() {
        return window;
    }


    /**
     * Handles events for a single connection/segment downloader.
     */
    private static class ConnectionHandler implements SegmentDownloaderListener {
        private final ProgressManagerWindow.DownloadMCListItem connectionItem;
        private final AddBanners banners;
        private final ProgressManagerWindow window;

        private long segmentStartTime;

        // Banner metadata fields
        private byte[] image;
        private String url;

        public ConnectionHandler(ProgressManagerWindow window,
                                 ProgressManagerWindow.DownloadMCListItem connectionItem,
                                 AddBanners banners) {
            this.window = window;
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
            connectionItem.getObject().setStatus("**Server** put us in a queue at position: " + e.getQueuePosition());
            window.getDownloadList().repaint();
        }

        @Override
        public void startSegment(SegmentDownloaderEvent e) {
            segmentStartTime = System.currentTimeMillis();

            connectionItem.getObject().setTotal(e.getLength());
            connectionItem.getObject().setProgress(0);
            connectionItem.getObject()
                    .setStatus("Downloading from " + e.getMysterFileStub().getMysterAddress());
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
                case 'u': // URLs are UTF-8 but java's UTF decoder needs the
                          // length in the first two bytes
                    byte[] temp_buffer = e.getCopyOfData();

                    if (temp_buffer.length > (0xFFFF))
                        break; // error URL is insanely long

                    byte[] final_buffer = new byte[temp_buffer.length + 2];

                    final_buffer[0] = (byte) ((temp_buffer.length >> 8) & 0xFF);
                    final_buffer[1] = (byte) ((temp_buffer.length) & 0xFF);

                    for (int i = 0; i < temp_buffer.length; i++) {
                        final_buffer[i + 2] = temp_buffer[i];
                    }

                    final var in =
                            new com.myster.net.stream.client.MysterDataInputStream(new java.io.ByteArrayInputStream(final_buffer));

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
        
        public ProgressManagerWindow.DownloadMCListItem getConnectionItem() {
            return connectionItem;
        }
    }


}

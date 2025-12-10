package com.myster.net.stream.client.msdownload;

import java.awt.Frame;

public interface MSDownloadListener {
    // Start of the overall download
    void startDownload(MultiSourceEvent event);

    // progress through the overall download
    void progress(MultiSourceEvent event);

    /**
     * start of a new segment downloader (sub download). Note that event
     * contains {@link SegmentDownloader} which you can use to register for events from
     * this sub download like progress etc.. using {@link SegmentDownloaderListener}
     */
    void startSegmentDownloader(MSSegmentEvent event);

    /**
     * End of this segment downloader. No more new file blocks will be requested from this server
     */
    void endSegmentDownloader(MSSegmentEvent event);

    /**
     * Download is ending.
     */
    void endDownload(MultiSourceEvent event);

    /**
     * Cleanup is done. File have been moved/copied or renamed to the final name. Resources have been closed.
     */
    void doneDownload(MultiSourceEvent event);
    
    /**
     * This is for dialog boxes and things like that. Feel free to return null
     * if you can't be bothered implementing this - the dialog box will simply
     * not have a parent.
     * 
     * @return a frame or null if you're not a frame.
     */
    Frame getFrame();
}
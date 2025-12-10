package com.myster.progress.ui;

import com.general.thread.Cancellable;
import com.myster.net.stream.client.msdownload.DownloadInitiator.DownloadInitiatorListener;
import com.myster.net.stream.client.msdownload.MSDownloadListener;
import com.myster.net.stream.client.msdownload.MSDownloadParams;

/**
 * If you want to start a download. Please add a method to this. I want to keep
 * all download starting in this object.
 */
public interface DownloadManager {
    /**
     * Use this when you want to start a download from scratch.
     *
     * @return connect this to listener to events / progress from the download
     */
    DownloadInitiatorListener bindToFileProgressGui(MSDownloadParams params);

    MSDownloadListener getMsDownloadListener(String filename, Cancellable cancellable);
}


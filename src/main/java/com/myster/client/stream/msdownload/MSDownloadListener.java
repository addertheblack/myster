package com.myster.client.stream.msdownload;

public interface MSDownloadListener {
    void startDownload(MultiSourceEvent event);

    void progress(MultiSourceEvent event);

    void startSegmentDownloader(MSSegmentEvent event);

    void endSegmentDownloader(MSSegmentEvent event);

    void endDownload(MultiSourceEvent event);

    void doneDownload(MultiSourceEvent event);
}
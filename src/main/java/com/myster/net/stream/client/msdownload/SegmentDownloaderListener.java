package com.myster.net.stream.client.msdownload;

public interface SegmentDownloaderListener {
    /*
     * Using the interface below this is the transition table. 1 -> 2 | 3 | 6 2 ->
     * 3 | 6 3 -> 4 | 6 4 -> 4 | 5 | 6 5 -> 2 | 3 | 6 6 -> end
     */

    public void connected(SegmentDownloaderEvent e); // 1

    public void queued(SegmentDownloaderEvent e); // 2

    public void startSegment(SegmentDownloaderEvent e); // 3

    public void downloadedBlock(SegmentDownloaderEvent e); // 4

    public void downloadedMetaData(SegmentMetaDataEvent e); // 4.5

    public void endSegment(SegmentDownloaderEvent e); // 5

    public void endConnection(SegmentDownloaderEvent e); // 6
}


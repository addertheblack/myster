package com.myster.net.stream.client.msdownload;

public class MSSegmentEvent {
    private final SegmentDownloader segmentDownloader;

    public MSSegmentEvent(SegmentDownloader segmentDownloader) {
        this.segmentDownloader = segmentDownloader;
    }

    public SegmentDownloader getSegmentDownloader() {
        return segmentDownloader;
    }
}
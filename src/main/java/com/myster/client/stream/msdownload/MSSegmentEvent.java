package com.myster.client.stream.msdownload;

public class MSSegmentEvent {
    private final SegmentDownloader segmentDownloader;

    public MSSegmentEvent(SegmentDownloader segmentDownloader) {
        this.segmentDownloader = segmentDownloader;
    }

    public SegmentDownloader getSegmentDownloader() {
        return segmentDownloader;
    }
}
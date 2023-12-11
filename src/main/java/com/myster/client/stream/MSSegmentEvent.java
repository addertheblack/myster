package com.myster.client.stream;

import com.general.events.GenericEvent;

public class MSSegmentEvent extends GenericEvent {
    public static final int START_SEGMENT = 100;

    public static final int END_SEGMENT = 101;

    SegmentDownloader segmentDownloader;

    public MSSegmentEvent(int id, SegmentDownloader segmentDownloader) {
        super(id);

        this.segmentDownloader = segmentDownloader;
    }

    public SegmentDownloader getSegmentDownloader() {
        return segmentDownloader;
    }
}
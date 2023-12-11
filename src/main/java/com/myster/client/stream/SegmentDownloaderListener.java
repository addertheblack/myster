package com.myster.client.stream;

import com.general.events.EventListener;
import com.general.events.GenericEvent;

public class SegmentDownloaderListener extends EventListener {
    /*
     * Using the interface below this is the transition table. 1 -> 2 | 3 | 6 2 ->
     * 3 | 6 3 -> 4 | 6 4 -> 4 | 5 | 6 5 -> 2 | 3 | 6 6 -> end
     */

    public void fireEvent(GenericEvent e) {
        switch (e.getID()) {
        case SegmentDownloaderEvent.CONNECTED:
            connected((SegmentDownloaderEvent) e);
            break;
        case SegmentDownloaderEvent.QUEUED:
            queued((SegmentDownloaderEvent) e);
            break;
        case SegmentDownloaderEvent.START_SEGMENT:
            startSegment((SegmentDownloaderEvent) e);
            break;
        case SegmentDownloaderEvent.DOWNLOADED_BLOCK:
            downloadedBlock((SegmentDownloaderEvent) e);
            break;
        case SegmentMetaDataEvent.DOWNLOADED_META_DATA:
            downloadedMetaData((SegmentMetaDataEvent) e);
            break;
        case SegmentDownloaderEvent.END_SEGMENT:
            endSegment((SegmentDownloaderEvent) e);
            break;
        case SegmentDownloaderEvent.END_CONNECTION:
            endConnection((SegmentDownloaderEvent) e);
            break;
        default:
            err();
            break;
        }
    }

    public void connected(SegmentDownloaderEvent e) {
    } //1

    public void queued(SegmentDownloaderEvent e) {
    } //2

    public void startSegment(SegmentDownloaderEvent e) {
    } //3

    public void downloadedBlock(SegmentDownloaderEvent e) {
    } //4

    public void downloadedMetaData(SegmentMetaDataEvent e) {
    } //4.5

    public void endSegment(SegmentDownloaderEvent e) {
    } //5

    public void endConnection(SegmentDownloaderEvent e) {
    } //6
}


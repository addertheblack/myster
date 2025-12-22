
package com.myster.net.stream.client.msdownload;

interface Controller {
    WorkSegment getNextWorkSegment(int requestedSize);

    void receiveExtraSegments(WorkSegment... workSegments);

    /**
     * When this call returns the param dataBlock buffer is REUSED!
     */
    void receiveDataBlock(DataBlock dataBlock);

    boolean removeDownload(SegmentDownloader downloader);

    /**
     * If it returns false it assumes ownership of the segment.
     * If it returns true YOU retain ownership of the segment.
     */
    boolean isOkToQueue(WorkSegment workSegment); // returns false if it's not ok to queue.
}
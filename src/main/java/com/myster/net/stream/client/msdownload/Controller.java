
package com.myster.net.stream.client.msdownload;

import java.io.IOException;
import java.util.Optional;

import com.myster.cid.ServerCid;

interface Controller {
    /** Claims an authenticated peer for this active segment, preventing duplicate CID transfers. */
    boolean claimSource(SegmentDownloader downloader, ServerCid cid);

    WorkSegment getNextWorkSegment(int requestedSize);

    void receiveExtraSegments(WorkSegment... workSegments);

    /**
     * Writes a block and its bitmap bit before remembering an optional first supplying identity.
     * When this call returns the dataBlock buffer is reused.
     * @throws IOException if the block could not be accepted; its source must not be recorded
     */
    void receiveDataBlock(DataBlock dataBlock, SegmentDownloader sender, Optional<ServerCid> source) throws IOException;

    boolean removeDownload(SegmentDownloader downloader);

    /**
     * If it returns false it assumes ownership of the segment.
     * If it returns true YOU retain ownership of the segment.
     */
    boolean isOkToQueue(WorkSegment workSegment); // returns false if it's not ok to queue.
}

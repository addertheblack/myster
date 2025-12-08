package com.myster.net.stream.client.msdownload;

public interface SegmentDownloaderListener {
    /*
     * Using the interface below this is the transition table. 1 -> 2 | 3 | 6 2 ->
     * 3 | 6 3 -> 4 | 6 4 -> 4 | 5 | 6 5 -> 2 | 3 | 6 6 -> end
     * 
     * 2 and 4.5 are interchangeable in terms of when they occur
     * 
     * also note that 2 -> 2 is an allowed transition.
     * 
     * Hopefully I didn't forget any
     */

    /**
     * We have made contact with the server
     */
    public void connected(SegmentDownloaderEvent e); // 1

    /**
     * Server tells us we are in a queue or are ready to go (queued = 0)
     */
    public void queued(SegmentDownloaderEvent e); // 2

    /**
     * Start downloading a new file segment. This can happen multiple times on one connection since
     * one can request to download multiple blocks without disconnection from the server)
     */
    public void startSegment(SegmentDownloaderEvent e); // 3

    /**
     * not really a "block".. more like one buffer full of data ie: some amount
     * of data has been downloaded within this segment. Please update the
     * progress bar... The "Block" wording comes from the tendency of the
     * download code of splitting things into chunks in.. usually of 64k.
     * Anyway.,. this event really means update your download progress.
     * 
     * @param e
     */
    public void downloadedBlock(SegmentDownloaderEvent e); // 4

    // This is used to send ad images. urls etc, server identifications etc..
    // "queued" events happen using the metadata system too .. so if you can get
    // a queued event you can also get a meta data event
    // although we only send one or the other.
    public void downloadedMetaData(SegmentMetaDataEvent e); // 4.5

    // done with that segment.. might start another...? who knows!
    public void endSegment(SegmentDownloaderEvent e); // 5

    // bye bye server cya
    public void endConnection(SegmentDownloaderEvent e); // 6
}


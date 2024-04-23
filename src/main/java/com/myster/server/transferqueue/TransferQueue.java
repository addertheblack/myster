package com.myster.server.transferqueue;

import java.io.IOException;

public abstract class TransferQueue {
    ////////////////////// Interface for implementors\\\\\\\\\\\\\\\\\\\

    /**
     * The method is respnsible for queuing. What happends is the Downloader
     * object being passed will have its "queued" methods called at LEAST once
     * every getMaxTimeInterval until its "download" method is called.
     * 
     * If there are too many items in the queue a MaxQueueLimitException will be
     * thrown. If there is an IO exception (either by the queued method, the
     * download method or otherwise) an IOException will be thrown
     */
    public abstract void doDownload(Downloader downloader) throws IOException,
            MaxQueueLimitException;

    /**
     * Returns the maximum time interval in ms that a thread will have to wait
     * between calls to queued while the download is queued. <br>
     * Note : This value is not particularly accurate at this writting owning to
     * the difficulty in determining the time it will take to call the queued
     * method for each Downloader object waiting int the queue.
     * 
     * Implementors can return -1 if they TransferQueue implementation does not
     * refresh.
     */
    public abstract int getMaxTimeInterval();

    /**
     * set the number of available positions available for downloading
     * simutaneous downloading. Implementors of this method should document the
     * behavior of their implementations with regard to what happened if the
     * number of download spots shrinks below the number of active downloaders
     * (do they get disconnected or what?)
     * <p>
     * Users will send -1 if there are infinite download spots. (it's rather
     * pointless ot have a Transfer queue if this is done, however).
     * <p>
     */
    public abstract void setDownloadSpots(int newSpots);
//
//    /**
//     * returns the number of simutaneous downloads allowed <br>
//     * Will return -1 if the number of simutaneous downloads is not limited
//     */
//    public abstract int getDownloadSpots();

    /**
     * returns the number of currently active downloads. This number i not
     * gurenteed to be less than the value returned by getDownloadSpots owing
     * the the possibility of resizing the number of active spots to a number
     * smaller than the number of active downloads at the time.
     */
    public abstract int getActiveDownloads();

    /**
     * returns the number of currently active downloads. This number is not
     * gurenteed to be less than the value returned by getDownloadSpots owing
     * the the possibility of resizing the number of active spots to a number
     * smaller than the number of active downloads at the time.
     */
    public abstract int getQueuedDownloads();

    /**
     * The maximum number of queued items that can be waiting in the queue
     * before doDownload throws an MaxQueueLimitException.
     * <p>
     * Implementors should document if this setting is constant or not. <br>
     * Implementors may return -1 if their queue has no upper bound.
     */
    public abstract int getMaxQueueLength();

    /**
     * sets The maximum number of queued items that can be waiting in the queue
     * before doDownload throws an MaxQueueLimitException.
     * <p>
     * Implementors should document if this setting method has any effect in
     * there implementation or not. <br>
     * sending -1 to this value should make the number of queue spots unlimited
     */
    public abstract void setMaxQueueLength(
            int numberOfAllowedSimutaneousDownloads);

}


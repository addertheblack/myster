package com.myster.transferqueue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.general.util.LinkedList;
import com.myster.net.MysterAddress;

/**
 * Provides a generic queue implementation. Most TransferQueue implementator
 * will want to subclass this.
 */

public abstract class AbstractDownloadQueue extends TransferQueue {
    public static final int UNLIMITED_QUEUE_LENGTH = -1;

    private static final int WAIT_TIME = 30 * 1000; //30 secs

    private List<DownloadTicket> downloads = new ArrayList<DownloadTicket>();

    private LinkedList downloadQueue = new LinkedList();

    private int maxDownloads;

    private int maxDownloadQueueLength = UNLIMITED_QUEUE_LENGTH;

    protected AbstractDownloadQueue(int numberOfDownloadSpots) {
        maxDownloads = numberOfDownloadSpots;
    }

    public final void doDownload(Downloader download) throws IOException,
            MaxQueueLimitException {
        DownloadTicket ticket = registerDownload(download);

        try {
            waitForMyTurn(ticket);

            doDownload(ticket);
        } finally {
            unregisterDownload(ticket);
        }
    }

    public int getMaxTimeInterval() {
        return WAIT_TIME;
    }

    public final int getActiveDownloads() {
        return downloads.size();
    }

    public final int getQueuedDownloads() {
        return downloadQueue.getSize();
    }

    public final int getDownloadSpots() {
        return maxDownloads;
    }

    /**
     * Subsclasses wishing to save their values between program launches should
     * over-ride saveDownloadSpots(int newSpots)
     */
    public final synchronized void setDownloadSpots(int newSpots) {
        maxDownloads = newSpots;

        saveDownloadSpotsInPrefs(newSpots);

        updateQueue();
    }

    protected void saveDownloadSpotsInPrefs(int newSpots) {
        // noting
    }

    public final int getMaxQueueLength() {
        return maxDownloadQueueLength;
    }

    public final void setMaxQueueLength(int numberOfAllowedSimutaneousDownloads) {
        maxDownloadQueueLength = numberOfAllowedSimutaneousDownloads;
    }

    private synchronized void waitForMyTurn(DownloadTicket ticket)
            throws IOException {
        while (true) {
            int lastQueuePosition;
            QueuedStats stats;
            synchronized (this) {
                if (ticket.isReadyToDownload())
                    return;

                stats = getQueuedStats(ticket);
            }

            lastQueuePosition = stats.getQueuePosition();
            ticket.getDownloader().queued(stats);

            if (ticket.isReadyToDownload())
                return; //just before sleep double check

            if (lastQueuePosition != getQueuedStats(ticket).getQueuePosition())
                continue;

            try {
                wait(WAIT_TIME); //wait on Lock
            } catch (InterruptedException ex) {
                // nothing
            }
        }

    }

    private QueuedStats getQueuedStats(DownloadTicket ticket) {
        return new QueuedStats(getQueuePosition(ticket));
    }

    private static void doDownload(DownloadTicket ticket) throws IOException {
        ticket.getDownloader().download();
    }

    // moves tickets from queue to download slow and sets their "go" flag.
    // Also notifys the other thread in the queue if a change has occured..
    private synchronized void updateQueue() {
        while (isExistFreeSpot() & (downloadQueue.getSize() != 0)) {
            DownloadTicket ticket = getNextToDownload();

            if (ticket == null)
                break; //nothing waiting to download.

            downloads.add(ticket);
            ticket.setReadyToDownload();

            notifyAll();
        }
    }

    private synchronized DownloadTicket getNextToDownload() {
        for (int i = 0; i < downloadQueue.getSize(); i++) {
            DownloadTicket ticket = (DownloadTicket) downloadQueue
                    .getElementAt(i);

            if (!isAlreadyDownloading(ticket.getDownloader().getAddress())) {
                downloadQueue.remove(ticket); //should return true else we're
                                              // screwed..

                return ticket;
            }

        }

        return null; //no next item found.
    }

    private synchronized boolean isAlreadyDownloading(MysterAddress address) {
        for (int i = 0; i < downloads.size(); i++) {
            DownloadTicket otherTicket = downloads.get(i);

            if (otherTicket.getDownloader().getAddress().equals(address)) {
                return true;
            }
        }

        return false;
    }

    private synchronized boolean isExistFreeSpot() {
        return (maxDownloads == UNLIMITED_QUEUE_LENGTH ? true
                : (maxDownloads - downloads.size()) > 0);
    }

    private synchronized DownloadTicket registerDownload(Downloader downloader)
            throws MaxQueueLimitException {
        if ((downloadQueue.getSize() >= maxDownloadQueueLength)
                && (maxDownloadQueueLength != UNLIMITED_QUEUE_LENGTH))
            throw new MaxQueueLimitException(
                    "Transfer Queue is already at max length");

        DownloadTicket ticket = new DownloadTicket(downloader);

        downloadQueue.addToTail(ticket);

        updateQueue();

        return ticket;
    }

    private synchronized void unregisterDownload(DownloadTicket ticket) {
        downloadQueue.remove(ticket); //if exists removes
        downloads.remove(ticket); //if exists removes

        updateQueue();
    }

    private synchronized int getQueuePosition(DownloadTicket ticket) {
        return downloadQueue.getPositionOf(ticket) + 1;
    }

    private static class DownloadTicket {
        Downloader downloader;

        volatile boolean readyToDownload = false;

        public DownloadTicket(Downloader downloader) {
            this.downloader = downloader;
        }

        public Downloader getDownloader() {
            return downloader;
        }

        public boolean isReadyToDownload() {
            return readyToDownload;
        }

        public void setReadyToDownload() {
            readyToDownload = true; //can't become un-ready to download
        }
    }
}
package com.myster.transferqueue;

/**
 * QueuedStats contains information that might be pertinant for Downloaders
 * waiting in a TransferQueue
 */

public class QueuedStats {
    public int queuePosition;

    public QueuedStats(int queuePosition) {
        this.queuePosition = queuePosition;
    }

    public int getQueuePosition() {
        return queuePosition;
    }
}
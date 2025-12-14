package com.myster.net.stream.client.msdownload;

// locally queued btw
public class QueuedMultiSourceEvent extends MultiSourceEvent {
    private final int queuePosition;

    QueuedMultiSourceEvent(int queuePosition, long initialOffset, long progress, long length, boolean cancelled) {
        super(initialOffset, progress, length, cancelled);
        this.queuePosition = queuePosition;
    }

    public int getQueuePosition() {
        return queuePosition;
    }
}

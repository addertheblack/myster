package com.myster.client.stream.msdownload;

public class MultiSourceEvent {
    private final long length;
    private final boolean cancelled;
    private final long progress;
    private final long initialOffset;

    MultiSourceEvent(long initialOffset, long progress, long length, boolean cancelled) {
        this.length = length;
        this.cancelled = cancelled;
        this.progress = progress;
        this.initialOffset = initialOffset;
    }

    public long getInitialOffset() {
        return initialOffset;
    }
    
    public long getProgress() {
        return progress;
    }

    public boolean isCancelled() {
        return cancelled;
    }
    
    public long getLength() {
        return length;
    }
}


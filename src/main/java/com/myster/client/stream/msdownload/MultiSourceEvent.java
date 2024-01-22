package com.myster.client.stream.msdownload;

import com.general.events.GenericEvent;

public class MultiSourceEvent extends GenericEvent {
    public static final int START_DOWNLOAD = 1;
    public static final int PROGRESS = 4; //is called when some data has come
                                          // in
    public static final int END_DOWNLOAD = 2;
    public static final int DONE_DOWNLOAD = 3; //is called when download has
                                               // ended AND file has finished

    private final long length;
    private final boolean cancelled;
    private final long progress;
    private final long initialOffset;

    MultiSourceEvent(int id, long initialOffset, long progress, long length, boolean cancelled) {
        super(id);
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


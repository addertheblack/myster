package com.myster.client.stream;

import com.general.events.GenericEvent;
import com.myster.search.MysterFileStub;

//For progress window stats.
//immutable
public class SegmentDownloaderEvent extends GenericEvent {
    public static final int CONNECTED = 1;

    public static final int QUEUED = 2;

    public static final int START_SEGMENT = 3;

    public static final int DOWNLOADED_BLOCK = 4;

    public static final int END_SEGMENT = 5;

    public static final int END_CONNECTION = 6;

    private final long offset;

    private final long progress;

    private final int queuePosition;

    private final long length;

    private final MysterFileStub stub;

    private final String queuedMessage;

    public SegmentDownloaderEvent(int id, long offset,
            long progress, int queuePosition, long length, MysterFileStub stub,
            String queuedMessage) {
        super(id);

        this.offset = offset;
        this.progress = progress;
        this.queuePosition = queuePosition;
        this.length = length;
        this.stub = stub;
        this.queuedMessage = queuedMessage;
    }

    public int getQueuePosition() {
        return queuePosition;
    }

    public long getOffset() {
        return offset;
    }

    public long getProgress() {
        return progress;
    }

    public long getLength() {
        return length;
    }

    public MysterFileStub getMysterFileStub() {
        return stub;
    }

    /**
     * If the download is being queued (ie: this event is a queued event), the remote server might
     * send a message as well. You can access this message using this command.
     * 
     * @return the server queued message or "" if no message.
     */
    public String getQueuedMessage() {
        return queuedMessage;
    }
}
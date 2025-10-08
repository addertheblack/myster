package com.myster.net.stream.client.msdownload;

import com.myster.search.MysterFileStub;

//For progress window stats.
//immutable
public class SegmentDownloaderEvent  {
    private final long offset;

    private final long progress;

    private final int queuePosition;

    private final long length;

    private final MysterFileStub stub;

    private final String queuedMessage;

    public SegmentDownloaderEvent(long offset,
                                  long progress,
                                  int queuePosition,
                                  long length,
                                  MysterFileStub stub,
                                  String queuedMessage) {

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

package com.myster.net.stream.client.msdownload;

// immutable!
record WorkSegment(long startOffset, long length, boolean recycled) {
    public WorkSegment(long startOffset, long length) {
        this(startOffset, length, false);
    }

    public boolean isEndSignal() {
        return (startOffset == 0) && (length == 0);
    }

    public WorkSegment recycled(boolean isRecycled) {
        return new WorkSegment(startOffset, length, isRecycled);
    }
}

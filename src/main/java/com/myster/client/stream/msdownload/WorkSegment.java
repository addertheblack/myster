
package com.myster.client.stream.msdownload;

// immutable!
final class WorkSegment {
    public final boolean recycled;

    public final long startOffset, length;

    public WorkSegment(long startOffset, long length) {
        this(startOffset, length, false);
    }

    private WorkSegment(long startOffset, long length, boolean isRecycled) {
        this.startOffset = startOffset;
        this.length = length;
        this.recycled = isRecycled;
    }

    public boolean isEndSignal() {
        return (startOffset == 0) && (length == 0);
    }

    public WorkSegment recycled(boolean isRecycled) {
        return new WorkSegment(startOffset, length, isRecycled);
    }
}
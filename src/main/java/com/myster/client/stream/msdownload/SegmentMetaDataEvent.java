package com.myster.client.stream.msdownload;

public class SegmentMetaDataEvent {
    final byte[] data;

    final byte type;

    public SegmentMetaDataEvent(final byte type, final byte[] data) {
        this.data = data;
        this.type = type;
    }

    public byte getType() {
        return type;
    }

    public byte[] getCopyOfData() {
        return (data.clone());
    }
}
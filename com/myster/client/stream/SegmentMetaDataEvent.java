package com.myster.client.stream;

import com.general.events.GenericEvent;

public class SegmentMetaDataEvent extends GenericEvent {
	public static final int DOWNLOADED_META_DATA = 45;
	
	final byte[]	data;
	final byte	 	type;
	
	public SegmentMetaDataEvent(final byte type, final byte[] data) {
		super(DOWNLOADED_META_DATA);
	
		this.data = data;
		this.type = type;
	}
	
	public byte getType() {
		return type;
	}
	
	public byte[] getCopyOfData() {
		return (byte[])(data.clone());
	}
}
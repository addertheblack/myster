package com.myster.client.stream;

import com.general.events.GenericEvent;

//import com.myster.client.stream.SegmentDownloader;

public class MultiSourceEvent extends GenericEvent {
	public static final int START_DOWNLOAD	= 1;
	public static final int START_SEGMENT	= 2;
	public static final int END_SEGMENT		= 3;

	//SegmentDownloader segmentDownloader;
	int refNumber;

	public MultiSourceEvent(int id, int refNumber) {//, SegmentDownloader segmentDownloader) {
		super(id);
		
		this.refNumber=refNumber;
		//this.segmentDownloader=segmentDownloader;
	}
	
	public int getReferenceNumber() {return refNumber;}
	
	//public SegmentDownloader getSegmentDownloadEventer() { return segmentDownloader; }
}
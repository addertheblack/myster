package com.myster.client.stream;



import com.general.events.*;



import com.myster.search.*;



//For progress window stats.

//immutable

public class SegmentDownloaderEvent extends GenericEvent {

	public static final int CONNECTED 			= 1;

	public static final int QUEUED 				= 2;

	public static final int START_SEGMENT 		= 3;

	public static final int DOWNLOADED_BLOCK	= 4;

	public static final int END_SEGMENT			= 5;

	public static final int END_CONNECTION 	= 6;



	final long 				offset;

	final long 				progress;

	final int 				queuePosition;

	final long				length;

	final SegmentDownloader	segmentDownloader;

	final MysterFileStub	stub;

	

	public SegmentDownloaderEvent(int id, SegmentDownloader segmentDownloader, long offset, long progress, int queuePosition, long length, MysterFileStub stub) {

		super(id);

		

		this.offset			= offset;

		this.progress		= progress;

		this.queuePosition	= queuePosition;

		this.length			= length;

		this.segmentDownloader		= segmentDownloader;

		this.stub		= stub;

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

	

	public SegmentDownloader getSegmentDownloader() {

		return segmentDownloader;

	}

	

	public MysterFileStub getMysterFileStub() {

		return stub;

	}

}
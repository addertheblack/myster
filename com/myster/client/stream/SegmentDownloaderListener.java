package com.myster.client.stream;

import com.general.events.*;

public class SegmentDownloaderListener extends EventListener {
	/*
		Using the interface below this is the transition table.
		1 -> 2 | 3 | 6
		2 -> 3 | 6
		3 -> 4 | 6
		4 -> 4 | 5 | 6
		5 -> 2 | 3 | 6
		6 -> end
	*/
	
	public void fireEvent(GenericEvent e) {
		SegmentDownloaderEvent event = (SegmentDownloaderEvent)e;
		
		switch (event.getID()) {
			case SegmentDownloaderEvent.CONNECTED:
				connected(event);
				break;
			case SegmentDownloaderEvent.QUEUED:
				queued(event);
				break;
			case SegmentDownloaderEvent.START_SEGMENT:
				startSegment(event);
				break;
			case SegmentDownloaderEvent.DOWNLOADED_BLOCK:
				downloadedBlock(event);
				break;
			case SegmentDownloaderEvent.END_SEGMENT:
				endSegment(event);
				break;
			case SegmentDownloaderEvent.END_CONNECTION:
				endConnection(event);
				break;
			default:
				err();
				break;
		}
	}
	
	public void connected(SegmentDownloaderEvent e) {}			//1
	public void queued(SegmentDownloaderEvent e) {}				//2
	public void startSegment(SegmentDownloaderEvent e) {}		//3
	public void downloadedBlock(SegmentDownloaderEvent e) {}	//4
	public void endSegment(SegmentDownloaderEvent e) {}			//5
	public void endConnection(SegmentDownloaderEvent e) {}		//6
}


package com.myster.client.stream;

import com.general.events.EventListener;
import com.general.events.GenericEvent;

public class MSDownloadListener extends EventListener {
// 1 -> 2 | 3
// 2 -> 2 | 3
// 3 -> 2 | 3

	public void fireEvent(GenericEvent e) {
		MultiSourceEvent event = (MultiSourceEvent)e;

		switch (event.getID()) {
	        case MultiSourceEvent.START_DOWNLOAD :
		        startDownload(event);
		        break;
	        case MultiSourceEvent.START_SEGMENT :
                startNewSegmentDownloader(event);
                break;
	        case MultiSourceEvent.END_SEGMENT :
                endSegmentDownloader(event);
                break;
	        default:
                err();
                break;
		}
	}

	public void startNewSegmentDownloader(MultiSourceEvent event) {}
	public void startDownload(MultiSourceEvent event) {}
	public void endSegmentDownloader(MultiSourceEvent event) {}
}

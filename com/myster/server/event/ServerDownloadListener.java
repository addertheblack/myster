/**
	...
*/


package com.myster.server.event;

import com.general.events.EventListener;
import com.general.events.GenericEvent;

public abstract class ServerDownloadListener extends EventListener {
	
	public final void fireEvent(GenericEvent e) {
		switch (e.getID()) {
			case ServerDownloadEvent.STARTED:
				downloadStarted((ServerDownloadEvent)e);
				break;
			case ServerDownloadEvent.BLOCKSENT:
				blockSent((ServerDownloadEvent)e);
				break;
			case ServerDownloadEvent.FINISHED:
				downloadFinished((ServerDownloadEvent)e);
				break;
			case ServerDownloadEvent.QUEUED:
				queued((ServerDownloadEvent)e);
				break;
			default:
				err();
		}
	}

	public void downloadStarted(ServerDownloadEvent e) {}
	public void blockSent(ServerDownloadEvent e) {}
	public void downloadFinished(ServerDownloadEvent e) {}
	public void queued(ServerDownloadEvent e) {}
}
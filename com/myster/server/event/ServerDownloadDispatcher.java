/**
	...
*/


package com.myster.server.event;

import com.general.events.SyncEventDispatcher;

public class ServerDownloadDispatcher extends SyncEventDispatcher {
	public void addServerDownloadListener(ServerDownloadListener l){ 
		addListener(l);
	}
	
	public void removeServerSearchListener(ServerDownloadListener l){
		removeListener(l);
	}
}
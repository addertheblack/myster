/**
	...
*/


package com.myster.server.event;

import com.myster.util.MysterThread;
import java.util.Vector;
import com.myster.server.DownloadInfo;
import com.general.events.SyncEventDispatcher;

public class ServerDownloadDispatcher extends SyncEventDispatcher {
	public void addServerDownloadListener(ServerDownloadListener l){ 
		addListener(l);
	}
	
	public void removeServerSearchListener(ServerDownloadListener l){
		removeListener(l);
	}
}
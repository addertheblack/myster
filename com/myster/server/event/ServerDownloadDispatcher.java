/**
	...
*/


package com.myster.server.event;

import com.myster.util.MysterThread;
import java.util.Vector;
import com.myster.server.DownloadInfo;
import com.general.events.SyncEventDispatcher;

public class ServerDownloadDispatcher extends SyncEventDispatcher {
	Vector listeners=new Vector(10,10);
	MysterThread thread;
	DownloadInfo info;
	
	public ServerDownloadDispatcher(DownloadInfo i) {
		info=i;
	}

	public void addServerDownloadListener(ServerDownloadListener l){ 
		addListener(l);
	}
	
	public void removeServerSearchListener(ServerDownloadListener l){
		removeListener(l);
	}

	public DownloadInfo getDownloadInfo() {
		return info;
	}
	
}
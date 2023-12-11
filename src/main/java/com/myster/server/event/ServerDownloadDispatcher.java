/**
 * ...
 */

package com.myster.server.event;

import com.general.events.SyncEventThreadDispatcher;

public class ServerDownloadDispatcher extends SyncEventThreadDispatcher {
    public void addServerDownloadListener(ServerDownloadListener l) {
        addListener(l);
    }

    public void removeServerSearchListener(ServerDownloadListener l) {
        removeListener(l);
    }
}
/*
 * Search Connection Section Dispatcher
 */

package com.myster.server.event;

import com.general.events.SyncEventDispatcher;

public class ServerSearchDispatcher extends SyncEventDispatcher {
    public void addServerSearchListener(ServerSearchListener l) {
        addListener(l);
    }

    public void removeServerSearchListener(ServerSearchListener l) {
        removeListener(l);
    }
}
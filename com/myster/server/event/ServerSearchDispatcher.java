/*
 * Search Connection Section Dispatcher
 */

package com.myster.server.event;

import com.general.events.SyncEventThreadDispatcher;

public class ServerSearchDispatcher extends SyncEventThreadDispatcher {
    public void addServerSearchListener(ServerSearchListener l) {
        addListener(l);
    }

    public void removeServerSearchListener(ServerSearchListener l) {
        removeListener(l);
    }
}
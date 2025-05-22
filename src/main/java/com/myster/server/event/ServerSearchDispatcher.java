/*
 * Search Connection Section Dispatcher
 */

package com.myster.server.event;

import com.general.events.NewGenericDispatcher;
import com.general.thread.Invoker;

public class ServerSearchDispatcher {
    private final NewGenericDispatcher<ServerSearchListener> dispatcher =
            new NewGenericDispatcher<>(ServerSearchListener.class, Invoker.EDT_NOW_OR_LATER);

    public void addServerSearchListener(ServerSearchListener l) {
        dispatcher.addListener(l);
    }

    public void removeServerSearchListener(ServerSearchListener l) {
        dispatcher.removeListener(l);
    }
    
    public ServerSearchListener fire() {
        return dispatcher.fire();
    }
}
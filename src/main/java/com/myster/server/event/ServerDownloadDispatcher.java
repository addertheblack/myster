package com.myster.server.event;

import com.general.events.NewGenericDispatcher;
import com.general.thread.Invoker;

public class ServerDownloadDispatcher  {
    private final NewGenericDispatcher<ServerDownloadListener> dispatcher =
            new NewGenericDispatcher<>(ServerDownloadListener.class, Invoker.EDT_NOW_OR_LATER);
    
    public void addServerDownloadListener(ServerDownloadListener l) {
        dispatcher.addListener(l);
    }

    public void removeServerSearchListener(ServerDownloadListener l) {
        dispatcher.removeListener(l);
    }
    
    public ServerDownloadListener fire() {
        return dispatcher.fire();
    }
}
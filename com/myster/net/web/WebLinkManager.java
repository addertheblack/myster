package com.myster.net.web;

import java.net.URL;

import com.general.events.SyncEventDispatcher;

public class WebLinkManager {
    private static SyncEventDispatcher dispatcher = new SyncEventDispatcher();

    public static void addWebLinkListener(WebLinkListener l) {
        dispatcher.addListener(l);
    }

    public static void removeWebLinkListener(WebLinkListener l) {
        dispatcher.removeListener(l);
    }

    public static int getNumberOfListeners() {
        return dispatcher.getNumberOfListeners();
    }

    /**
     * Call this if you want a URL to open in a web browser. Must be done on the event thread.
     * @param url
     */
    public static void openURL(URL url) {
        dispatcher.fireEvent(new WebLinkEvent(WebLinkEvent.LINK, url));
    }
}


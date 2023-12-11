package com.myster.net.web;

import java.net.URL;

import com.general.events.GenericEvent;

public class WebLinkEvent extends GenericEvent {
    public static final int LINK = 1;

    private URL url;

    public WebLinkEvent(int id, URL url) {
        super(id);
        this.url = url;
    }

    public URL getURL() {
        return url;
    }
}
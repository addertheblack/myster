package com.myster.net.web;

import java.net.URL;

public class WebLinkEvent  {
    private URL url;

    public WebLinkEvent(  URL url) {
        this.url = url;
    }

    public URL getURL() {
        return url;
    }
}
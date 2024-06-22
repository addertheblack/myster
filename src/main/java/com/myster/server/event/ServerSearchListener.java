package com.myster.server.event;

public interface ServerSearchListener {
    public void searchRequested(ServerSearchEvent e);
    public void searchResult(ServerSearchEvent e);
}
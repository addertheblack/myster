package com.myster.server.event;

public interface ServerDownloadListener  {
    public void downloadSectionStarted(ServerDownloadEvent e);

    public void downloadStarted(ServerDownloadEvent e);

    public void blockSent(ServerDownloadEvent e);

    public void downloadFinished(ServerDownloadEvent e);

    public void queued(ServerDownloadEvent e);

    public void downloadSectionFinished(ServerDownloadEvent e);
}
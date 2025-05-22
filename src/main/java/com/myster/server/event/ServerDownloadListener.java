package com.myster.server.event;

public interface ServerDownloadListener  {
    /**
     * First method called. This is called whenever a new d/l connection is created.
     */
    public void downloadSectionStarted(ServerDownloadEvent e);

    /**
     * Called if the download gets queued. Called between {@link #downloadSectionStarted(ServerDownloadEvent)} and
     * {@link #downloadStarted(ServerDownloadEvent)}. Note: Might not be called at all if d/l is never queued.
     * 
     * Not called after first downloadStarted()
     */
    public void queued(ServerDownloadEvent e);
    
    /**
     * Called when the download of a block is started. Can be called many times per d/l
     */
    public void downloadStarted(ServerDownloadEvent e);

    /**
     * Called when a segment has finished. Is paired with a {@link #downloadStarted(ServerDownloadEvent)}
     */
    public void downloadFinished(ServerDownloadEvent e);


    /** When d/l is done. Called once. */
    public void downloadSectionFinished(ServerDownloadEvent e);
}
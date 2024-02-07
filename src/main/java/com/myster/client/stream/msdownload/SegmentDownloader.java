package com.myster.client.stream.msdownload;

public interface SegmentDownloader {
    public void addListener(SegmentDownloaderListener listener);

    public void removeListener(SegmentDownloaderListener listener);

    public boolean isDead();
    
    public void start();
    
    public boolean isActive();
    
    public void flagToEnd();
}
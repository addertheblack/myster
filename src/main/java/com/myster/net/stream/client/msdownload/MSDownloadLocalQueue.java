package com.myster.net.stream.client.msdownload;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.prefs.Preferences;

public class MSDownloadLocalQueue {
    private static final String PREF_KEY = "Max Local Downloads";
    
    private final Preferences prefs;
    
    // LinkedList to track insertion order - first element is the oldest/longest-running download
    private final LinkedList<MultiSourceDownload> downloading = new LinkedList<>();
    
    private final List<MultiSourceDownload> queued = new ArrayList<>();
    
    public MSDownloadLocalQueue(Preferences prefs) {
        this.prefs = prefs;
    }
    
    public synchronized void addToQueue(MultiSourceDownload download) {
        // Don't add if already in queue or downloading
        if (queued.contains(download) || downloading.contains(download)) {
            return;
        }
        
        download.pauseDirectly(); // Ensure it's paused
        queued.add(download);
        updateQueuePositions(); // Notify all queued downloads of their positions
        processQueue();
    }

    public synchronized void removeFromQueue(MultiSourceDownload download) {
        boolean wasQueued = queued.remove(download);
        boolean wasDownloading = downloading.remove(download);
        
        download.pauseDirectly();
        
        // If the download was in the queue or downloading, notify it's now unqueued
        if (wasQueued || wasDownloading) {
            download.notifyUnqueued();
        }
        
        updateQueuePositions(); // Update positions after removal
        processQueue();
    }
    
    /**
     * Forces a download to start immediately, even if the download slots are full.
     * If at capacity, moves the longest-running download (first in LinkedList) to the queue.
     * This is used when resume() is called on a queued download.
     * 
     * @param download the download to force-start
     */
    public synchronized void forceStartDownload(MultiSourceDownload download) {
        // Remove from queue if present
        queued.remove(download);
        
        // If already downloading, nothing to do
        if (downloading.contains(download)) {
            return;
        }
        
        // If at capacity, move the oldest downloading to the queue
        if (downloading.size() >= getMaxSimultaneousDownloads() && !downloading.isEmpty()) {
            MultiSourceDownload oldest = downloading.removeFirst(); // FIFO - oldest first
            oldest.pauseDirectly();
            queued.add(0, oldest); // Add to front of queue (position 1)
        }
        
        // Start the requested download
        downloading.addLast(download); // Add to end (newest)
        startDownload(download);
        
        // Update queue positions for all affected downloads
        updateQueuePositions();
    }

    private synchronized void processQueue() {
        while (downloading.size() < getMaxSimultaneousDownloads() && !queued.isEmpty()) {
            MultiSourceDownload element = queued.remove(0);
            downloading.addLast(element); // Add to end to maintain FIFO order
            startDownload(element);
        }
        
        updateQueuePositions(); // Update positions after processing
    }
    
    /**
     * Notify all queued downloads of their current position in the queue.
     */
    private synchronized void updateQueuePositions() {
        for (int i = 0; i < queued.size(); i++) {
            queued.get(i).notifyQueued(i + 1); // 1-based position
        }
    }
    
    private int getMaxSimultaneousDownloads() {
        return prefs.getInt(PREF_KEY, 4);
    }
    
    private static void startDownload(MultiSourceDownload download) {
        download.startDirectly();
    }   
}
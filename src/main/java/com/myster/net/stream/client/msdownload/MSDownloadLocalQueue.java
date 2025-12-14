package com.myster.net.stream.client.msdownload;

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

public class MSDownloadLocalQueue {
    private static final String PREF_KEY = "Max Local Downloads";
    
    private final Preferences prefs;
    
    private final List<MultiSourceDownload> downloading = new ArrayList<>();
    
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
        downloading.remove(download);
        queued.remove(download);
        
        download.pauseDirectly();
        updateQueuePositions(); // Update positions after removal
        processQueue();
    }

    private synchronized void processQueue() {
        while (downloading.size() < getMaxSimultaneousDownloads() && !queued.isEmpty()) {
            MultiSourceDownload element = queued.remove(0);
            downloading.add(element);
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
        return prefs.getInt(PREF_KEY, 1);
    }
    
    private static void startDownload(MultiSourceDownload download) {
        download.startDirectly();
    }   
}
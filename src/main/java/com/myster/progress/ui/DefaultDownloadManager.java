package com.myster.progress.ui;

import com.general.thread.Cancellable;
import com.general.util.Util;
import com.myster.net.stream.client.msdownload.DownloadInitiator.DownloadInitiatorListener;
import com.myster.net.stream.client.msdownload.MSDownloadListener;
import com.myster.net.stream.client.msdownload.MSDownloadParams;
import com.myster.ui.MysterFrameContext;

import static com.general.util.Util.ensureEventDispatchThread;

public class DefaultDownloadManager implements DownloadManager {
    private ProgressManagerWindow progressManagerWindow;
    private MysterFrameContext context;
    
    public DefaultDownloadManager() {
        ensureEventDispatchThread();
    }
    
    public ProgressManagerWindow getProgressManagerWindow() {
        return progressManagerWindow;
    }
    
    /**
     * Initialize and restore window locations from preferences. Call this
     * before restartDownloads() to ensure the window is ready.
     * 
     * MUST be called on the EDT.
     * 
     * @return the number of windows restored (0 or 1)
     */
    public int initWindowLocations() {
        ensureEventDispatchThread();

        if (progressManagerWindow == null) {
            throw new IllegalStateException("MysterFrameContext not set before initWindowLocations()");
        }

        return progressManagerWindow.initWindowLocations();
    }

    @Override
    public DownloadInitiatorListener bindToFileProgressGui(MSDownloadParams params) {
        // The window already exists from the constructor, just use it
        // Create a listener for this specific download
        ProgressManagerDownloadListener listener = new ProgressManagerDownloadListener(
            progressManagerWindow,
            params
        );
        
        // Wrap it in EDT-safe wrapper to ensure all UI updates happen on EDT
        return new EdtDownloadInitiatorListener(listener);
    }

    @Override
    public MSDownloadListener getMsDownloadListener(String filename, Cancellable cancellable) {
        return new ProgManDownloadHandler(progressManagerWindow, filename, cancellable);
    }

    public void setContext(MysterFrameContext context) {
        ensureEventDispatchThread();
        progressManagerWindow = new ProgressManagerWindow(context);
    }
}

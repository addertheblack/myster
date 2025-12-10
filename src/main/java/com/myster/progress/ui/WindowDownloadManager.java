package com.myster.progress.ui;

import com.general.thread.Cancellable;
import com.myster.net.stream.client.msdownload.DownloadInitiator.DownloadInitiatorListener;
import com.myster.net.stream.client.msdownload.MSDownloadHandler;
import com.myster.net.stream.client.msdownload.MSDownloadListener;
import com.myster.net.stream.client.msdownload.MSDownloadParams;
import com.myster.ui.MysterFrameContext;

public class WindowDownloadManager implements DownloadManager {
    private final MysterFrameContext context;

    public WindowDownloadManager(MysterFrameContext context) {
        this.context = context;
    }
    
    @Override
    public DownloadInitiatorListener bindToFileProgressGui(MSDownloadParams params) {
        return new EdtFileProgressWindow(context, params);
    }

    @Override
    public MSDownloadListener getMsDownloadListener(String filename, Cancellable cancellable) {
        final FileProgressWindow progress = showProgres(filename);
        
        return new MSDownloadHandler(progress, cancellable);
    }
    
    private FileProgressWindow showProgres(final String filename) {
        final FileProgressWindow progress = new FileProgressWindow(context);
        progress.setTitle("Downloading " + filename);
        progress.show();
        return progress;
    }
}

package com.myster.client.stream;

import com.general.events.EventListener;
import com.general.events.GenericEvent;

public abstract class MSDownloadListener extends EventListener {
    public final void fireEvent(GenericEvent e) {
        switch (e.getID()) {
        case MultiSourceEvent.START_DOWNLOAD:
            startDownload((MultiSourceEvent) e);
            break;
        case MultiSourceEvent.PROGRESS:
            progress((MultiSourceEvent) e);
            break;
        case MSSegmentEvent.START_SEGMENT:
            startSegmentDownloader((MSSegmentEvent) e);
            break;
        case MSSegmentEvent.END_SEGMENT:
            endSegmentDownloader((MSSegmentEvent) e);
            break;
        case MultiSourceEvent.END_DOWNLOAD:
            endDownload((MultiSourceEvent) e);
            break;
        case MultiSourceEvent.DONE_DOWNLOAD:
            doneDownload((MultiSourceEvent) e);
            break;
        default:
            err();
            break;
        }
    }

    public void startDownload(MultiSourceEvent event) {
    }

    public void progress(MultiSourceEvent event) {
    }

    public void startSegmentDownloader(MSSegmentEvent event) {
    }

    public void endSegmentDownloader(MSSegmentEvent event) {
    }

    public void endDownload(MultiSourceEvent event) {
    }

    public void doneDownload(MultiSourceEvent event) {
    }
}
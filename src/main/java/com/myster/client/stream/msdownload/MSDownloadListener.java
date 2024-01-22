package com.myster.client.stream.msdownload;

import com.general.events.EventListener;
import com.general.events.GenericEvent;

public interface MSDownloadListener extends EventListener {
    default void fireEvent(GenericEvent e) {
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

    void startDownload(MultiSourceEvent event);

    void progress(MultiSourceEvent event);

    void startSegmentDownloader(MSSegmentEvent event);

    void endSegmentDownloader(MSSegmentEvent event);

    void endDownload(MultiSourceEvent event);

    void doneDownload(MultiSourceEvent event);
}
package com.myster.client.stream;

import com.general.events.GenericEvent;

public class MultiSourceEvent extends GenericEvent {
    public static final int START_DOWNLOAD = 1;

    public static final int PROGRESS = 4; //is called when some data has come
                                          // in

    public static final int END_DOWNLOAD = 2;

    public static final int DONE_DOWNLOAD = 3; //is called when download has
                                               // ended AND file has finished

    MultiSourceDownload msDownload;

    public MultiSourceEvent(int id, MultiSourceDownload msDownload) {
        super(id);

        this.msDownload = msDownload;
    }

    public MultiSourceDownload getMultiSourceDownload() {
        return msDownload;
    }
}


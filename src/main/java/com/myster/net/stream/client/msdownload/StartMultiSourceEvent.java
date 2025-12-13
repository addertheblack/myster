package com.myster.net.stream.client.msdownload;

public class StartMultiSourceEvent extends MultiSourceEvent {
    private final MSDownloadControl control;

    StartMultiSourceEvent(MSDownloadControl control, long initialOffset, long progress, long length, boolean cancelled) {
        super(initialOffset, progress, length, cancelled);
        this.control = control;
    }

    public MSDownloadControl getControl() {
        return control;
    }
}

package com.myster.net.stream.client.msdownload;

public interface MSDownloadControl {
    void pause();
    void resume(); // not start(). Label things how they are used
    void cancel();
    boolean isPaused();
    boolean isActive(); // true if download is not dead or done
}

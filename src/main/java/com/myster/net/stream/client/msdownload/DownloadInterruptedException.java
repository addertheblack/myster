package com.myster.net.stream.client.msdownload;

/**
 * The download could not start because the startup thread was interrupted.
 */
public class DownloadInterruptedException extends DownloadStartException {
    public DownloadInterruptedException(Throwable cause) {
        super("Download interrupted.", cause);
    }
}

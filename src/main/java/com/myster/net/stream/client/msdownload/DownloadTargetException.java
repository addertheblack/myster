package com.myster.net.stream.client.msdownload;

/**
 * A local destination problem prevented Myster from preparing the target file
 * for a download.
 * <p>
 * This exception family is for local folder and target-file setup failures,
 * not asynchronous network failures after the download has started.
 */
public class DownloadTargetException extends DownloadStartException {
    public DownloadTargetException(String message) {
        super(message);
    }

    public DownloadTargetException(String message, Throwable cause) {
        super(message, cause);
    }
}

package com.myster.net.stream.client.msdownload;

/**
 * Base class for failures that prevent a download from being scheduled or
 * prepared locally.
 */
public class DownloadStartException extends Exception {
    public DownloadStartException(String message) {
        super(message);
    }

    public DownloadStartException(String message, Throwable cause) {
        super(message, cause);
    }
}

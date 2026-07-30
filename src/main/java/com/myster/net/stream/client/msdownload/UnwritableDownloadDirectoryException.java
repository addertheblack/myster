package com.myster.net.stream.client.msdownload;

import java.nio.file.Path;

/**
 * The selected download destination cannot be written to or prepared.
 */
public class UnwritableDownloadDirectoryException extends DownloadTargetException {
    private final Path path;

    public UnwritableDownloadDirectoryException(Path path, String message) {
        super(message + ": " + path);
        this.path = path;
    }

    public UnwritableDownloadDirectoryException(Path path, String message, Throwable cause) {
        super(message + ": " + path, cause);
        this.path = path;
    }

    public Path path() {
        return path;
    }
}

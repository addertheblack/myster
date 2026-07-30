package com.myster.net.stream.client.msdownload;

import java.nio.file.Path;

/**
 * The selected download destination path does not identify a usable directory.
 */
public class InvalidDownloadDirectoryException extends DownloadTargetException {
    private final Path path;

    public InvalidDownloadDirectoryException(Path path, String message) {
        super(message + ": " + path);
        this.path = path;
    }

    public Path path() {
        return path;
    }
}

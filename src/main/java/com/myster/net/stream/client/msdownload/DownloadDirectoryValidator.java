package com.myster.net.stream.client.msdownload;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Validates already selected or configured download directories.
 * <p>
 * This class is intentionally non-GUI. Callers that need to ask the user for a
 * directory should do so before calling into the download core.
 */
public final class DownloadDirectoryValidator {
    private DownloadDirectoryValidator() {
    }

    /**
     * Validates a known download directory without asking the user.
     *
     * @param path selected or configured download directory
     * @return absolute normalized directory path
     * @throws DownloadTargetException if the path is missing, not a directory,
     *         or not writable
     */
    public static Path validateDownloadDirectory(Path path) throws DownloadTargetException {
        if (path == null) {
            throw new NullPointerException("path is null");
        }

        Path normalizedPath = path.toAbsolutePath().normalize();
        if (!Files.exists(normalizedPath)) {
            throw new InvalidDownloadDirectoryException(normalizedPath,
                                                        "The download folder does not exist");
        }

        if (!Files.isDirectory(normalizedPath)) {
            throw new InvalidDownloadDirectoryException(normalizedPath,
                                                        "The download path is not a folder");
        }

        if (!Files.isWritable(normalizedPath)) {
            throw new UnwritableDownloadDirectoryException(normalizedPath,
                                                           "The download folder is not writable");
        }

        return normalizedPath;
    }
}

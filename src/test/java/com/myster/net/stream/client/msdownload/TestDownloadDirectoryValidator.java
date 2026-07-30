package com.myster.net.stream.client.msdownload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestDownloadDirectoryValidator {
    @TempDir
    Path tempDir;

    @Test
    void validateDownloadDirectory_validDirectory_returnsAbsolutePath() throws Exception {
        Path result = DownloadDirectoryValidator.validateDownloadDirectory(tempDir);

        assertEquals(tempDir.toAbsolutePath().normalize(), result);
    }

    @Test
    void validateDownloadDirectory_missingPath_throwsInvalidDownloadDirectory() {
        Path missingPath = tempDir.resolve("missing");

        assertThrows(InvalidDownloadDirectoryException.class,
                     () -> DownloadDirectoryValidator.validateDownloadDirectory(missingPath));
    }

    @Test
    void validateDownloadDirectory_filePath_throwsInvalidDownloadDirectory()
            throws Exception {
        Path file = tempDir.resolve("not-a-directory.txt");
        Files.writeString(file, "data");

        assertThrows(InvalidDownloadDirectoryException.class,
                     () -> DownloadDirectoryValidator.validateDownloadDirectory(file));
    }
}

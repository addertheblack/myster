package com.myster.ui;

import java.nio.file.Path;

import com.myster.net.stream.client.msdownload.DownloadInterruptedException;
import com.myster.net.stream.client.msdownload.DownloadServerConnectionException;
import com.myster.net.stream.client.msdownload.DownloadStartException;
import com.myster.net.stream.client.msdownload.InvalidDownloadDirectoryException;
import com.myster.net.stream.client.msdownload.UnwritableDownloadDirectoryException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestDownloadStartErrorDialog {
    @Test
    void messageFor_invalidDownloadDirectory() {
        assertEquals("The download folder is not valid: /tmp/nope",
                     DownloadStartErrorDialog
                             .messageFor(new InvalidDownloadDirectoryException(Path.of("/tmp/nope"),
                                                                               "ignored")));
    }

    @Test
    void messageFor_unwritableDownloadDirectory() {
        assertEquals("Myster cannot write to the download folder: /tmp/nope",
                     DownloadStartErrorDialog
                             .messageFor(new UnwritableDownloadDirectoryException(Path.of("/tmp/nope"),
                                                                                  "ignored")));
    }

    @Test
    void messageFor_serverConnection() {
        assertEquals("Myster could not connect to the server: example.com",
                     DownloadStartErrorDialog
                             .messageFor(new DownloadServerConnectionException("example.com",
                                                                               new Exception())));
    }

    @Test
    void messageFor_interrupted() {
        assertEquals("The download was interrupted before it could start.",
                     DownloadStartErrorDialog
                             .messageFor(new DownloadInterruptedException(new InterruptedException())));
    }

    @Test
    void messageFor_unknownStartupFailure() {
        assertEquals("Myster could not start the download.",
                     DownloadStartErrorDialog
                             .messageFor(new DownloadStartException("ignored")));
    }
}

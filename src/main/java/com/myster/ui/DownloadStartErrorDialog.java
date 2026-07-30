package com.myster.ui;

import java.awt.Frame;
import java.util.function.Consumer;

import com.general.util.AnswerDialog;
import com.general.util.Util;
import com.myster.net.stream.client.msdownload.DownloadInterruptedException;
import com.myster.net.stream.client.msdownload.DownloadServerConnectionException;
import com.myster.net.stream.client.msdownload.DownloadStartException;
import com.myster.net.stream.client.msdownload.InvalidDownloadDirectoryException;
import com.myster.net.stream.client.msdownload.UnwritableDownloadDirectoryException;

/**
 * UI helper for showing download startup failures on the Swing EDT.
 */
public final class DownloadStartErrorDialog {
    private DownloadStartErrorDialog() {
    }

    /**
     * Builds a callback suitable for {@code MSDownloadParams}.
     *
     * @param parentFrame parent frame for the error dialog
     * @return callback that shows download startup failures on the EDT
     */
    public static Consumer<DownloadStartException> handler(Frame parentFrame) {
        return exception -> showOnEdt(parentFrame, exception);
    }

    /**
     * Shows a download startup failure on the Swing EDT.
     *
     * @param parentFrame parent frame for the error dialog
     * @param exception startup failure to show
     */
    public static void showOnEdt(Frame parentFrame, DownloadStartException exception) {
        Util.invokeLater(() -> AnswerDialog.simpleAlert(parentFrame, messageFor(exception)));
    }

    static String messageFor(DownloadStartException exception) {
        if (exception instanceof InvalidDownloadDirectoryException invalidDirectory) {
            return "The download folder is not valid: " + invalidDirectory.path();
        }

        if (exception instanceof UnwritableDownloadDirectoryException unwritableDirectory) {
            return "Myster cannot write to the download folder: "
                    + unwritableDirectory.path();
        }

        if (exception instanceof DownloadServerConnectionException serverConnection) {
            return "Myster could not connect to the server: " + serverConnection.server();
        }

        // this one should be impossible
        if (exception instanceof DownloadInterruptedException) {
            return "The download was interrupted before it could start.";
        }

        return "Myster could not start the download.";
    }
}

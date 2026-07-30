package com.myster.ui;

import javax.swing.JFileChooser;
import java.awt.Frame;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

import com.general.util.AnswerDialog;
import com.myster.filemanager.FileTypeListManager;
import com.myster.net.stream.client.msdownload.DownloadDirectoryValidator;
import com.myster.net.stream.client.msdownload.DownloadTargetException;
import com.myster.type.MysterType;

/**
 * UI helper for selecting a writable base directory for downloads.
 * <p>
 * This class only handles the shared directory decision. It does not construct
 * per-file target paths, append partial-download suffixes, or start downloads.
 */
public final class DownloadDirectoryChooser {
    private static final String OK_BUTTON = "OK";
    private static final String CANCEL_BUTTON = "Cancel";

    private DownloadDirectoryChooser() {
    }

    /**
     * Asks for a download directory, optionally trying a configured directory
     * first when the command is not explicit {@code Download To...}.
     *
     * @param parentFrame parent frame for validation alerts
     * @param title chooser title used if the user needs to pick a folder
     * @param alwaysAskForDirectory true to skip the configured directory
     * @param fileManager file manager that owns configured type paths
     * @param type type whose configured download path should be tried first
     * @return selected writable directory, or empty if the user cancels
     */
    public static Optional<Path> chooseWritableDownloadDirectory(
            Frame parentFrame,
            String title,
            boolean alwaysAskForDirectory,
            FileTypeListManager fileManager,
            MysterType type) {
        return chooseWritableDownloadDirectory(parentFrame,
                                               title,
                                               alwaysAskForDirectory,
                                               fileManager,
                                               type,
                                               new SwingFolderDialog());
    }

    static Optional<Path> chooseWritableDownloadDirectory(Frame parentFrame,
                                                         String title,
                                                         Optional<Path> configuredDirectory,
                                                         FolderDialog folderDialog) {
        configuredDirectory = Objects.requireNonNullElse(configuredDirectory, Optional.empty());
        if (configuredDirectory.isPresent()) {
            try {
                return Optional.of(DownloadDirectoryValidator
                        .validateDownloadDirectory(configuredDirectory.get()));
            } catch (DownloadTargetException exception) {
                if (!shouldAskForAnotherFolder(parentFrame, folderDialog, exception)) {
                    return Optional.empty();
                }
            }
        }

        while (true) {
            Path selectedPath = folderDialog.askForFolder(parentFrame, title);
            if (selectedPath == null) {
                return Optional.empty();
            }

            try {
                return Optional.of(DownloadDirectoryValidator
                        .validateDownloadDirectory(selectedPath));
            } catch (DownloadTargetException exception) {
                if (!shouldAskForAnotherFolder(parentFrame, folderDialog, exception)) {
                    return Optional.empty();
                }
            }
        }
    }

    static Optional<Path> chooseWritableDownloadDirectory(
            Frame parentFrame,
            String title,
            boolean alwaysAskForDirectory,
            FileTypeListManager fileManager,
            MysterType type,
            FolderDialog folderDialog) {
        if (!alwaysAskForDirectory) {
            Objects.requireNonNull(fileManager, "fileManager");
            Objects.requireNonNull(type, "type");
        }
        String configuredPath = alwaysAskForDirectory ? null
                : fileManager.getPathFromType(type);
        Optional<Path> configuredDirectory = configuredPath == null ? Optional.empty()
                : Optional.of(Path.of(configuredPath));
        return chooseWritableDownloadDirectory(parentFrame,
                                               title,
                                               configuredDirectory,
                                               folderDialog);
    }

    private static boolean shouldAskForAnotherFolder(Frame parentFrame,
                                                     FolderDialog folderDialog,
                                                     DownloadTargetException exception) {
        String answer = folderDialog.showAlert(parentFrame,
                                               DownloadStartErrorDialog.messageFor(exception),
                                               new String[] { OK_BUTTON,
                                                       CANCEL_BUTTON });
        return !CANCEL_BUTTON.equals(answer);
    }

    interface FolderDialog {
        Path askForFolder(Frame parentFrame, String title);

        String showAlert(Frame parentFrame, String message, String[] buttons);
    }

    private static class SwingFolderDialog implements FolderDialog {
        @Override
        public Path askForFolder(Frame parentFrame, String title) {
            JFileChooser chooser = new JFileChooser(title);
            chooser.setDialogTitle(title);
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setAcceptAllFileFilterUsed(false);

            int result = chooser.showSaveDialog(parentFrame == null
                    ? AnswerDialog.getCenteredFrame() : parentFrame);

            if (result == JFileChooser.APPROVE_OPTION) {
                return chooser.getSelectedFile().toPath();
            }

            return null;
        }

        @Override
        public String showAlert(Frame parentFrame, String message, String[] buttons) {
            return AnswerDialog.simpleAlert(parentFrame, message, buttons);
        }
    }
}

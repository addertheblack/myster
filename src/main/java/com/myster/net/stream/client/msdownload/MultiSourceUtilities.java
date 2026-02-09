package com.myster.net.stream.client.msdownload;

import java.awt.Frame;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.logging.Logger;

import com.general.util.AnswerDialog;
import com.myster.hash.FileHash;
import com.myster.mml.MessagePak;

public class MultiSourceUtilities {
    private static final Logger log = Logger.getLogger(MultiSourceUtilities.class.getName());

    private static final String EXTENSION = ".i";

    private static final String OK_BUTTON = "OK", CANCEL_BUTTON = "Cancel",
            WRITE_OVER = "Write-Over", RENAME = "Rename";

    /**
     * Gets a file to download to, creating necessary subdirectories.
     * 
     * @param fileName the name of the file to download (without extension)
     * @param parentFrame the parent frame for dialogs
     * @param absolutePathToDownloadFolderBaseDir optional absolute path to the base download directory
     * @param relativePath relative path for subdirectories (must be relative)
     * @return A File object representing the file to download to, or null if cancelled
     */
    public static File getFileToDownloadTo(String fileName, Frame parentFrame, Optional<Path> absolutePathToDownloadFolderBaseDir, Path relativePath) {
        return getFileToDownloadTo(fileName, parentFrame, absolutePathToDownloadFolderBaseDir, relativePath, new DefaultDialogProvider());
    }
    
    /**
     * Gets a file to download to, creating necessary subdirectories.
     * Package-private for testing.
     * 
     * @param fileName the name of the file to download (without extension)
     * @param parentFrame the parent frame for dialogs
     * @param absolutePathToDownloadFolderBaseDir optional absolute path to the base download directory
     * @param relativePath relative path for subdirectories (must be relative)
     * @param dialogProvider provider for showing dialogs (for testing)
     * @return A File object representing the file to download to, or null if cancelled
     */
    static File getFileToDownloadTo(String fileName, Frame parentFrame, Optional<Path> absolutePathToDownloadFolderBaseDir, Path relativePath, DialogProvider dialogProvider) {
        // Validate that relativePath is actually relative
        if (relativePath.isAbsolute()) {
            throw new IllegalArgumentException("relativePath must be a relative path, got: " + relativePath);
        }
        
        // Validate that absolutePathToDownloadFolderBaseDir is absolute (if present)
        if (absolutePathToDownloadFolderBaseDir.isPresent() && !absolutePathToDownloadFolderBaseDir.get().isAbsolute()) {
            throw new IllegalArgumentException("absolutePathToDownloadFolderBaseDir must be an absolute path, got: " + absolutePathToDownloadFolderBaseDir.get());
        }
        
        Path baseDir = absolutePathToDownloadFolderBaseDir.orElse(null);
        Path targetDirectory = null;

        // Loop until we get a valid writable base directory or user cancels
        while (targetDirectory == null) {
            // Get the base directory
            if (baseDir == null) {
                baseDir = dialogProvider.askForFolder("Select a folder to save the file in");
                if (baseDir == null) {
                    return null; // User cancelled
                }
            }

            // Validate base directory exists and is a directory
            // This should basically always happen
            if (!Files.exists(baseDir)) {
                String answer = dialogProvider.showAlert(parentFrame,
                        "The directory " + baseDir + " does not exist.",
                        new String[] { OK_BUTTON, CANCEL_BUTTON });
                if (answer.equals(CANCEL_BUTTON)) {
                    return null;
                }
                baseDir = null; // Ask again
                continue;
            }
            
            if (!Files.isDirectory(baseDir)) {
                String answer = dialogProvider.showAlert(parentFrame,
                        "The path " + baseDir + " is not a directory.",
                        new String[] { OK_BUTTON, CANCEL_BUTTON });
                if (answer.equals(CANCEL_BUTTON)) {
                    return null;
                }
                baseDir = null; // Ask again
                continue;
            }
            
            // Try to create the subdirectories
            targetDirectory = baseDir.resolve(relativePath);
            try {
                Files.createDirectories(targetDirectory);
            } catch (IOException e) {
                log.warning("Could not create directories: " + targetDirectory + " - " + e.getMessage());
                String answer = dialogProvider.showAlert(parentFrame,
                        "Cannot create subdirectories in " + baseDir + ". The directory may be read-only.",
                        new String[] { OK_BUTTON, CANCEL_BUTTON });
                if (answer.equals(CANCEL_BUTTON)) {
                    return null;
                }
                baseDir = null; // Ask again
                targetDirectory = null; // Reset for next iteration
                continue;
            }
            
            // Verify the directory is writable
            if (!Files.isWritable(targetDirectory)) {
                String answer = dialogProvider.showAlert(parentFrame,
                        "Cannot write to directory " + targetDirectory + ". It appears to be read-only.",
                        new String[] { OK_BUTTON, CANCEL_BUTTON });
                if (answer.equals(CANCEL_BUTTON)) {
                    return null;
                }
                baseDir = null; // Ask again
                targetDirectory = null; // Reset for next iteration
            }
        }
        
        // Build the final file path
        Path file = targetDirectory.resolve(fileName + EXTENSION);
        Path finalFile = targetDirectory.resolve(fileName);
        
        // Check if file already exists and handle overwrite
        // Note that this will cause problems if you've got two downloads for the same file going.
        if (Files.exists(file) || Files.exists(finalFile)) {
            String answer = dialogProvider.showAlert(parentFrame,
                    "A file by the name of " + file.getFileName() + " already exists. What do you want to do?",
                    new String[] { WRITE_OVER, CANCEL_BUTTON });
            if (answer.equals(CANCEL_BUTTON)) {
                return null;
            } else if (answer.equals(WRITE_OVER)) {
                try {
                    if (Files.exists(file)) {
                        Files.delete(file);
                    }
                } catch (IOException e) {
                    log.warning("Could not delete file: " + file + " - " + e.getMessage());
                    dialogProvider.showAlert(parentFrame, "Could not delete the file.", new String[] { OK_BUTTON });
                    return null;
                }
                
                try {
                    if (Files.exists(finalFile)) {
                        Files.delete(finalFile);
                    }
                } catch (IOException e) {
                    log.warning("Could not delete file: " + finalFile + " - " + e.getMessage());
                    dialogProvider.showAlert(parentFrame, "Could not delete the file.", new String[] { OK_BUTTON });
                    return null;
                }
            }
        }
        
        return file.toFile();
    }
    
    private static Path askUserForANewFolder(String name) {
        javax.swing.JFileChooser chooser = new javax.swing.JFileChooser(name);
        chooser.setDialogTitle(name);// "Select a folder to save the file in");
        chooser.setFileSelectionMode(javax.swing.JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        
        int result = chooser.showSaveDialog(com.general.util.AnswerDialog.getCenteredFrame());
        
        if (result == javax.swing.JFileChooser.APPROVE_OPTION) {
            return chooser.getSelectedFile().toPath();
        }
        
        return null; // canceled
    }


    public static boolean isWritable(File file) {
        boolean fileExsts = file.exists();

        //try {
        //    RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");
        //} catch (FileNotFoundException ex) {
        //    return false;
        //}
        if (!fileExsts && file.exists())
            file.delete();

        return true;
    }
    
    public interface SimpleAlert {
        void simpleAlert(String s);
    }
    
    /**
     * Interface for showing dialogs - allows mocking in tests.
     */
    public interface DialogProvider {
        /**
         * Show an alert dialog with multiple buttons.
         * @param parentFrame the parent frame
         * @param message the message to display
         * @param buttons the button labels
         * @return the label of the button that was clicked
         */
        String showAlert(Frame parentFrame, String message, String[] buttons);
        
        /**
         * Ask the user to select a folder.
         * @param title the dialog title
         * @return the selected folder path, or null if cancelled
         */
        Path askForFolder(String title);
    }
    
    /**
     * Default implementation that uses real GUI dialogs.
     */
    public static class DefaultDialogProvider implements DialogProvider {
        @Override
        public String showAlert(Frame parentFrame, String message, String[] buttons) {
            return AnswerDialog.simpleAlert(parentFrame, message, buttons);
        }
        
        @Override
        public Path askForFolder(String title) {
            return askUserForANewFolder(title);
        }
    }

    public static void moveFileToFinalDestination(final File sourceFile, SimpleAlert dialogBox) {
        final String FILE_ENDING = ".i";

        // Make sure the file ends with the expected suffix.
        if (!sourceFile.getName().endsWith(FILE_ENDING)) {
            dialogBox.simpleAlert("Could not rename file \"" + sourceFile.getName()
                    + "\" because it does not end with " + FILE_ENDING + ".");
            return;
        }

        // Remove the extra ending to get the intended final file name.
        String sourcePath = sourceFile.getAbsolutePath();
        String finalPath = sourcePath.substring(0, sourcePath.length() - FILE_ENDING.length());
        File finalFile = findFinalFileName(finalPath);
        if (finalFile == null) {
            dialogBox.simpleAlert("Could not rename file from \"" + sourceFile.getName()
                    + "\" because the final file name already exists.");
            return;
        }

        // Attempt to rename the file.
        if (!sourceFile.renameTo(finalFile)) {
            dialogBox
                    .simpleAlert("Could not rename file from \"" + sourceFile.getName() + "\" to \""
                            + finalFile.getName() + "\" because an unspecified error occurred.");
        }
    }

    private static File findFinalFileName(String finalPath) {
        File candidate = new File(finalPath);

        // If a file by that name already exists, try adding "-1", "-2", etc.
        if (!candidate.exists()) {
            return candidate;
        }
        // Extract the base name and extension.
        String fileName = candidate.getName();
        String baseName = fileName;
        String extension = "";
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex != -1) {
            baseName = fileName.substring(0, dotIndex);
            extension = fileName.substring(dotIndex); // includes the dot
        }

        File parentDir = candidate.getParentFile();
        // Start at 2 (as per your change) and try up to 100 iterations.
        for (int counter = 2; counter <= 100; counter++) {
            candidate = new File(parentDir, baseName + "-" + counter + extension);
            if (!candidate.exists()) {
                return candidate;
            }
        }
        
        return null;
    }


    /**
     * Returns a file object containing the path that a multi-source object will
     * be downloaded to. Uses the private data path for incomplete downloads.
     * <p>
     * This routine is not stable as it is dependent on the way multi-source
     * downloads are downloaded.
     */
    public static File getIncomingDirectory() throws IOException {
        File file = new File(com.myster.application.MysterGlobals.getPrivateDataPath(), "Incoming");

        if ((file.exists()) && (file.isDirectory()))
            return file;

        if (!file.exists()) {
            file.mkdir();
            return file;
        } else {
            throw new IOException(
                    "Could not make an incoming directory because there is a file in the way.");
        }
    }

    private static Path askUserForANewFile(String name) {
        java.awt.FileDialog dialog = new java.awt.FileDialog(com.general.util.AnswerDialog
                .getCenteredFrame(), "What do you want to save the file as?",
                java.awt.FileDialog.SAVE);
        dialog.setFile(name);
        dialog.setDirectory(name);

        dialog.setVisible(true);

        if (dialog.getFile() == null)
            return null; // canceled

        Path directory = Path.of(dialog.getDirectory());
        return directory.resolve(dialog.getFile() + EXTENSION);
    }


    public static FileHash getHashFromStats(MessagePak fileStats) throws IOException {
        Optional<byte[]> hashBytes =
                fileStats.getByteArray("/hash/" + com.myster.hash.HashManager.MD5);

        return hashBytes
                .map(hash -> com.myster.hash.SimpleFileHash
                        .buildFileHash(com.myster.hash.HashManager.MD5, hash))
                .orElse(null);
    }

    public static long getLengthFromStats(MessagePak fileStats) throws IOException {
        Optional<Long> fileLengthString = fileStats.getLong("/size");

        if (fileLengthString.isEmpty())
            throw new IOException("Stats MML does not contain the wanted info.");

        return fileLengthString.get();
    }

    public static void debug(String msg) {
        log.fine(msg);
    }

    private static final String STOP_DOWNLOAD = "Kill";

    private static final String CANCEL = "Don't Kill";

    /**
     * Asks the user to confirm stopping this download.
     */
    public static boolean confirmCancel(Frame progress) {
        final String choice = AnswerDialog.simpleAlert(progress,
                "Are you sure you want to kill this download?", new String[] { STOP_DOWNLOAD,
                        CANCEL });
        return (choice.equals(STOP_DOWNLOAD));
    }
}

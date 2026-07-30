package com.myster.net.stream.client.msdownload;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

import com.myster.hash.FileHash;
import com.myster.mml.MessagePak;

public class MultiSourceUtils {
    private static final Logger log = Logger.getLogger(MultiSourceUtils.class.getName());

    private static final String EXTENSION = ".i";

    /**
     * Gets a file to download to, creating necessary subdirectories.
     * 
     * @param fileName the name of the file to download (without extension)
     * @param absolutePathToDownloadFolderBaseDir absolute base download directory
     * @param relativePath relative path for subdirectories (must be relative)
     * @param existingTargetHandler decides what to do if the partial or final target exists
     * @return A File object representing the file to download to, or null if cancelled
     * @throws DownloadStartException if the destination directory cannot be used
     */
    public static File getFileToDownloadTo(String fileName,
                                           Path absolutePathToDownloadFolderBaseDir,
                                           Path relativePath,
                                           ExistingDownloadTargetHandler existingTargetHandler)
            throws DownloadStartException {
        existingTargetHandler = Objects.requireNonNullElse(existingTargetHandler,
                                                           ExistingDownloadTargetHandler
                                                                   .CANCEL_DOWNLOAD);

        // Validate that relativePath is actually relative
        if (relativePath.isAbsolute()) {
            throw new IllegalArgumentException("relativePath must be a relative path, got: " + relativePath);
        }
        
        // Validate that absolutePathToDownloadFolderBaseDir is absolute (if present)
        if (!absolutePathToDownloadFolderBaseDir.isAbsolute()) {
            throw new IllegalArgumentException("absolutePathToDownloadFolderBaseDir must be an absolute path, got: " + absolutePathToDownloadFolderBaseDir);
        }

        Path baseDir = DownloadDirectoryValidator
                .validateDownloadDirectory(absolutePathToDownloadFolderBaseDir);
        Path targetDirectory = baseDir.resolve(relativePath).normalize();
        try {
            Files.createDirectories(targetDirectory);
        } catch (IOException exception) {
            log.warning("Could not create directories: " + targetDirectory + " - "
                    + exception.getMessage());
            throw new UnwritableDownloadDirectoryException(targetDirectory,
                                                           "Cannot create download subfolders",
                                                           exception);
        }

        if (!Files.isDirectory(targetDirectory)) {
            throw new InvalidDownloadDirectoryException(targetDirectory,
                                                        "The download target path is not a folder");
        }

        if (!Files.isWritable(targetDirectory)) {
            throw new UnwritableDownloadDirectoryException(targetDirectory,
                                                           "Cannot write to download folder");
        }
        
        // Build the final file path
        Path file = targetDirectory.resolve(fileName + EXTENSION);
        Path finalFile = targetDirectory.resolve(fileName);
        
        // Check if file already exists and handle overwrite
        // Note that this will cause problems if you've got two downloads for the same file going.
        if (Files.exists(file) || Files.exists(finalFile)) {
            ExistingDownloadTargetHandler.Decision decision = existingTargetHandler
                    .chooseForExistingTarget(file, finalFile);
            if (decision == ExistingDownloadTargetHandler.Decision.CANCEL) {
                return null;
            } else if (decision == ExistingDownloadTargetHandler.Decision.OVERWRITE) {
                try {
                    if (Files.exists(file)) {
                        Files.delete(file);
                    }
                } catch (IOException e) {
                    log.warning("Could not delete file: " + file + " - " + e.getMessage());
                    throw new UnwritableDownloadDirectoryException(file,
                                                                   "Could not delete existing partial download file",
                                                                   e);
                }
                
                try {
                    if (Files.exists(finalFile)) {
                        Files.delete(finalFile);
                    }
                } catch (IOException e) {
                    log.warning("Could not delete file: " + finalFile + " - " + e.getMessage());
                    throw new UnwritableDownloadDirectoryException(finalFile,
                                                                   "Could not delete existing download file",
                                                                   e);
                }
            }
        }
        
        return file.toFile();
    }

    public interface SimpleAlert {
        void simpleAlert(String s);
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
}

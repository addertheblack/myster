package com.myster.filemanager;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import com.myster.hash.FileHash;
import com.myster.mml.MessagePack;

public class FileItem {
    private static final Logger LOGGER = Logger.getLogger(FileItem.class.getName());
    
    private final Path path;
    private FileHash[] fileHashes;

    private final Path root;

    /**
     * Constructor using Path objects (preferred)
     */
    public FileItem(Path root, Path path) {
        this.root = root;
        this.path = path;
    }

    /**
     * Returns the java.io.File object (for backward compatibility)
     * 
     * @deprecated use {@link #getPath()}
     */
    @Deprecated
    public File getFile() {
        return path.toFile();
    }
    
    /**
     * Returns the Path object (preferred)
     */
    public Path getPath() {
        return path;
    }

    private int getIndex(String hashType) {
        if (fileHashes == null)
            return -1;

        for (int i = 0; i < fileHashes.length; i++) {
            if (fileHashes[i].getHashName().equalsIgnoreCase(hashType))
                return i;
        }

        LOGGER.fine("Could not find hash type: " + hashType);
        
        return -1;
    }

    public synchronized void setHash(FileHash[] fileHashes) {
        this.fileHashes = fileHashes;
    }

    public synchronized FileHash getHash(String hashType) {
        int index = getIndex(hashType);

        if (index == -1)
            return null;

        return fileHashes[index];
    }

    /**
     * If the file hash been hashed at some point this returns true.
     */
    public boolean isHashed() {
        return (fileHashes != null);
    }

    public String getName() {
        return FileTypeList.mergePunctuation(path.getFileName().toString());
    }

    public String getFullPath() {
        return path.toAbsolutePath().toString();
    }

    public int hashCode() {
        return path.hashCode();
    }
    
    public boolean equals(Object o) {
        try {
            FileItem item = (FileItem) o;

            return (path.equals(item.path));
        } catch (ClassCastException _) {
            return false;
        }
    }

    public static final String HASH_PATH = "/hash/";

    private volatile long fileSize = -1;
    public MessagePack getMessagePackRepresentation() {
        MessagePack messagePack = MessagePack.newEmpty();

        if (path != null) {
            try {
                fileSize = fileSize == -1 ? java.nio.file.Files.size(path) : fileSize;
                messagePack.putLong("/size", fileSize);

                if (fileHashes != null) {
                    for (int i = 0; i < fileHashes.length; i++) {
                        // Use byte arrays for hashes instead of strings for better compactness
                        messagePack.putByteArray(HASH_PATH + fileHashes[i].getHashName().toLowerCase(), 
                                               fileHashes[i].getBytes());
                    }
                }

                var pathElements = extractPathFromFileAndRoot(root, path);
                messagePack.putStringArray("/path", pathElements.toArray(new String[] {}));
            } catch (java.io.IOException e) {
                LOGGER.warning("Failed to get file size: " + e.getMessage());
            }
        }

        return messagePack;
    }
    
    static List<String> extractPathFromFileAndRoot(Path root, Path filePath) {
        if (root == null || filePath == null) {
            throw new IllegalArgumentException("Root and filePath cannot be null");
        }
        
        var pathElements = new ArrayList<String>();
        
        // Normalize paths to handle symbolic links, relative paths, etc.
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedFilePath = filePath.toAbsolutePath().normalize();
        
        if (normalizedRoot.equals(normalizedFilePath)) {
            return pathElements;
        }
        
        // Check if filePath is under root and get relative path
        if (!normalizedFilePath.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("File '" + filePath + "' is not under root '" + root + "'");
        }
        
        // Get relative path and extract components
        Path relativePath = normalizedRoot.relativize(normalizedFilePath);
        for (int i = 0; i < relativePath.getNameCount(); i++) {
            pathElements.add(relativePath.getName(i).toString());
        }
        
        return pathElements;
    }

    /*
     * public boolean isMatch(String queryString) { }
     */

}
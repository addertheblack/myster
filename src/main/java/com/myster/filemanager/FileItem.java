package com.myster.filemanager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import com.myster.hash.FileHash;
import com.myster.mml.MessagePack;

public class FileItem {
    private static final Logger LOGGER = Logger.getLogger(FileItem.class.getName());
    
    private final File file;
    private FileHash[] fileHashes;

    private final File root;

    public FileItem(File root, File file) {
        this.root = root;
        this.file = file;
    }

    /**
     * Returns the java.io.File object
     */
    public File getFile() {
        return file;
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
        return FileTypeList.mergePunctuation(file.getName());
    }

    public String getFullPath() {
        return file.getAbsolutePath();
    }

    public int hashCode() {
        return file.hashCode();
    }
    
    public boolean equals(Object o) {
        try {
            FileItem item = (FileItem) o;

            return (file.equals(item.file));
        } catch (ClassCastException _) {
            return false;
        }
    }

    public static final String HASH_PATH = "/hash/";

    private volatile long fileSize = -1;
    public MessagePack getMessagePackRepresentation() {
        MessagePack messagePack = MessagePack.newEmpty();

        if (file != null) {
            fileSize = fileSize == -1 ? file.length() : fileSize;
            messagePack.putLong("/size", fileSize);

            if (fileHashes != null) {
                for (int i = 0; i < fileHashes.length; i++) {
                    // Use byte arrays for hashes instead of strings for better compactness
                    messagePack.putByteArray(HASH_PATH + fileHashes[i].getHashName().toLowerCase(), 
                                           fileHashes[i].getBytes());
                }
            }

            var pathElements = new ArrayList<String>();
            extractPathFromFileAndRoot(root, file, pathElements);
            messagePack.putStringArray("/path", pathElements.toArray(new String[] {}));
        }

        return messagePack;
    }
    
    private static void extractPathFromFileAndRoot(File root, File filePath, List<String> path) {
        if (root == null || filePath == null) {
            throw new IllegalArgumentException("Root and filePath cannot be null");
        }
        
        // Normalize paths to handle symbolic links, relative paths, etc.
        File normalizedRoot = root.getAbsoluteFile();
        File normalizedFilePath = filePath.getAbsoluteFile();
        
        if (normalizedRoot.equals(normalizedFilePath)) {
            return;
        }
        
        // Build path from file back to root (iterative, not recursive)
        List<String> reversePath = new ArrayList<>();
        File current = normalizedFilePath;
        
        while (current != null && !normalizedRoot.equals(current)) {
            reversePath.add(current.getName());
            current = current.getParentFile();
        }
        
        // Check if we actually reached the root
        if (current == null) {
            throw new IllegalArgumentException("File '" + filePath + "' is not under root '" + root + "'");
        }
        
        
        // Add path elements in correct order (reverse of what we collected)
        for (int i = reversePath.size() - 1; i >= 0; i--) {
            path.add(reversePath.get(i));
        }
    }

    /*
     * public boolean isMatch(String queryString) { }
     */

}
package com.myster.filemanager;

import java.io.File;
import java.util.logging.Logger;

import com.myster.hash.FileHash;
import com.myster.mml.MessagePack;

public class FileItem {
    private static final Logger LOGGER = Logger.getLogger(FileItem.class.getName());
    
    private final File file;
    private FileHash[] fileHashes;

    public FileItem(File file) {
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
        } catch (ClassCastException ex) {
            return false;
        }
    }

    public static final String HASH_PATH = "/hash/";

    public MessagePack getMessagePackRepresentation() {
        MessagePack messagePack = MessagePack.newEmpty();

        if (file != null) {
            messagePack.putLong("/size", file.length());

            if (fileHashes != null) {
                for (int i = 0; i < fileHashes.length; i++) {
                    // Use byte arrays for hashes instead of strings for better compactness
                    messagePack.putByteArray(HASH_PATH + fileHashes[i].getHashName().toLowerCase(), 
                                           fileHashes[i].getBytes());
                }
            }
        }

        return messagePack;
    }

    /*
     * public boolean isMatch(String queryString) { }
     */

}
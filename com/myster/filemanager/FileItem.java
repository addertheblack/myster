package com.myster.filemanager;

import java.io.File;

import com.myster.hash.FileHash;
import com.myster.hash.FileHashEvent;
import com.myster.hash.FileHashListener;
import com.myster.hash.HashManager;
import com.myster.mml.MML;

public class FileItem {
    private final File file;

    private FileHash[] fileHashes;

    public FileItem(File file) {
        this.file = file;

        HashManager.findHashNoneBlocking(file, new FileHashListener() {
            public void foundHash(FileHashEvent e) {
                fileHashes = e.getHashes();
            }
        });
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

        System.out.println("Could not find hash type: " + hashType);
        return -1;
    }

    private synchronized void setHash(FileHash[] fileHashes) {
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
        return file.getName();
    }

    public String getFullPath() {
        return file.getAbsolutePath();
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

    public MML getMMLRepresentation() {
        MML mml = new MML();

        if (file != null) {
            mml.put("/size", "" + file.length());

            if (fileHashes != null) {
                for (int i = 0; i < fileHashes.length; i++) {
                    mml.put(HASH_PATH
                            + fileHashes[i].getHashName().toLowerCase(),
                            fileHashes[i].toString());
                }
            }
        }

        return mml;
    }

    /*
     * public boolean isMatch(String queryString) {
     *  }
     */

}
package com.myster.hash;

import java.io.File;

//immutable
public class FileHashEvent {
    public static final int FOUND_HASH = 1;

    private FileHash[] hashes;

    private File file;

    public FileHashEvent(FileHash[] hashes, File file) {
        this.hashes = hashes;
        this.file = file;
    }

    public FileHash[] getHashes() {
        return  hashes.clone();
    }

    public File getFile() {
        return file;
    }
}
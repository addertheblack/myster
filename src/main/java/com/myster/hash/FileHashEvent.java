package com.myster.hash;

import java.io.File;

import com.general.events.GenericEvent;

//immutable
public class FileHashEvent extends GenericEvent {
    public static final int FOUND_HASH = 1;

    private FileHash[] hashes;

    private File file;

    public FileHashEvent(int id, FileHash[] hashes, File file) {
        super(id);

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
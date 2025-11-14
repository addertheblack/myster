package com.myster.hash;

import java.nio.file.Path;

//immutable
public class FileHashEvent {
    public static final int FOUND_HASH = 1;

    private FileHash[] hashes;

    private Path path;

    public FileHashEvent(FileHash[] hashes, Path path) {
        this.hashes = hashes;
        this.path = path;
    }

    public FileHash[] getHashes() {
        return  hashes.clone();
    }
    
    public Path getPath() {
        return path;
    }
}
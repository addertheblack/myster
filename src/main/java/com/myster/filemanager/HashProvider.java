
package com.myster.filemanager;

import java.nio.file.Path;

import com.myster.hash.FileHashListener;

public interface HashProvider {
    /**
     * Path-based version (preferred). Default implementation converts to File for backward compatibility.
     */
    void findHashNonBlocking(Path path, FileHashListener listener);
}
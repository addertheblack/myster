
package com.myster.filemanager;

import java.io.File;

import com.myster.hash.FileHashListener;

public interface HashProvider {
    void findHashNonBlocking(File file, FileHashListener listener);
}
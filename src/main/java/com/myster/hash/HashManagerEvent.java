package com.myster.hash;

import java.io.File;

//immutable
public class HashManagerEvent {
    private final boolean enabled;

    private final File file;

    private final long progress;

    public HashManagerEvent(boolean enabled) {
        this(enabled, null, -1);
    }

    public HashManagerEvent(boolean enabled, File file, long progress) {
        this.enabled = enabled;
        this.file = file;
        this.progress = progress;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Returns file being processed or null if not applicable.
     */
    public File getFile() {
        return file;
    }

    /**
     * Returns the progress through the current file (or -1 if not applicable)
     */
    public long getProgress() {
        return progress;
    }
}
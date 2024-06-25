package com.myster.hash;

public interface HashManagerListener {
    void enabledStateChanged(HashManagerEvent e);
    void fileHashStart(HashManagerEvent e);
    void fileHashProgress(HashManagerEvent e);
    void fileHashEnd(HashManagerEvent e);
}
package com.myster.hash;

import com.general.events.EventListener;
import com.general.events.GenericEvent;

public abstract class FileHashListener extends EventListener {
    public void fireEvent(GenericEvent e) {
        FileHashEvent event = (FileHashEvent) e;

        switch (event.getID()) {
        case FileHashEvent.FOUND_HASH:
            foundHash(event);
            break;
        default:
            err();
        }
    }

    public abstract void foundHash(FileHashEvent e);
}
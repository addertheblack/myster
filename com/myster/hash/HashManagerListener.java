package com.myster.hash;

import com.general.events.EventListener;
import com.general.events.GenericEvent;

public abstract class HashManagerListener extends EventListener {
    public void fireEvent(GenericEvent e) {
        HashManagerEvent event = (HashManagerEvent) e;

        switch (event.getID()) {
        case HashManagerEvent.ENABLED_STATE_CHANGED:
            enabledStateChanged(event);
            break;
        case HashManagerEvent.START_HASH:
            fileHashStart(event);
            break;
        case HashManagerEvent.PROGRESS_HASH:
            fileHashProgress(event);
            break;
        case HashManagerEvent.END_HASH:
            fileHasEnd(event);
            break;
        default:
            err();
        }
    }

    public void enabledStateChanged(HashManagerEvent e) {
    }

    public void fileHashStart(HashManagerEvent e) {
    }

    public void fileHashProgress(HashManagerEvent e) {
    }

    public void fileHasEnd(HashManagerEvent e) {
    }
}
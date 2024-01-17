/**
 * 
 * Details the types of routines done by the connection manager.
 *  
 */

package com.myster.server.event;

import com.general.events.EventListener;
import com.general.events.GenericEvent;

public class ConnectionManagerListener implements EventListener {
    public void fireEvent(GenericEvent e) {
        switch (e.getID()) {
        case ConnectionManagerEvent.SECTIONCONNECT:
            sectionEventConnect((ConnectionManagerEvent) e);
            break;
        case ConnectionManagerEvent.SECTIONDISCONNECT:
            sectionEventDisconnect((ConnectionManagerEvent) e);
            break;
        default:
            err();
        }

    }

    public void sectionEventConnect(ConnectionManagerEvent e) {
    }

    public void sectionEventDisconnect(ConnectionManagerEvent e) {
    }
}
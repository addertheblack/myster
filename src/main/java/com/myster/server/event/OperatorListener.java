/**
 * 
 * Details the types of routines done by the connection manager.
 *  
 */

package com.myster.server.event;

import com.general.events.EventListener;
import com.general.events.GenericEvent;

public abstract class OperatorListener implements EventListener {

    public final void fireEvent(GenericEvent e) {
        OperatorEvent event = (OperatorEvent) e;

        switch (e.getID()) {
        case OperatorEvent.PING:
            pingEvent(event);
            break;
        case OperatorEvent.DISCONNECT:
            disconnectEvent(event);
            break;
        case OperatorEvent.CONNECT:
            connectEvent(event);
            break;
        default:
            err();
        }
    }

    // a "ping" is a connect / diconnect without any connection section.
    public void pingEvent(OperatorEvent e) {
    }

    public void disconnectEvent(OperatorEvent e) {
    }

    public void connectEvent(OperatorEvent e) {
    }
}
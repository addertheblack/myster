/**
 * 
 * ...
 *  
 */

package com.myster.server.event;

import com.general.events.EventListener;
import com.general.events.GenericEvent;

public abstract class ServerSearchListener extends EventListener { //check

    public final void fireEvent(GenericEvent e) {
        switch (e.getID()) {
        case ServerSearchEvent.REQUESTED:
            searchRequested((ServerSearchEvent) e);
            break;
        case ServerSearchEvent.RESULT:
            searchResult((ServerSearchEvent) e);
            break;
        default:
            err();
        }
    }

    public abstract void searchRequested(ServerSearchEvent e);

    public abstract void searchResult(ServerSearchEvent e);
}
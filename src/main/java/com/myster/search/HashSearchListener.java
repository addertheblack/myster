package com.myster.search;

import com.general.events.EventListener;
import com.general.events.GenericEvent;

public class HashSearchListener implements EventListener {
    public void fireEvent(GenericEvent e) {
        HashSearchEvent event = (HashSearchEvent) e;

        switch (event.getID()) {
        case HashSearchEvent.START_SEARCH:
            startSearch(event);
            break;
        case HashSearchEvent.SEARCH_RESULT:
            searchResult(event);
            break;
        case HashSearchEvent.END_SEARCH:
            endSearch(event);
            break;
        default:
            err();
            break;
        }
    }

    public void startSearch(HashSearchEvent event) {
    }

    public void searchResult(HashSearchEvent event) {
    }

    public void endSearch(HashSearchEvent event) {
    }
}
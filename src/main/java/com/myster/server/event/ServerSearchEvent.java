/**
 * ...
 */

package com.myster.server.event;

import com.myster.net.MysterAddress;
import com.myster.type.MysterType;

;

public class ServerSearchEvent extends ServerEvent {
    public final static int REQUESTED = 0;

    public final static int RESULTS = 1;

    private String searchString;
    private String[] results;
    private MysterType type;

    public ServerSearchEvent(MysterAddress ip, int section,
            String searchString, MysterType type, String[] results) {
        super(ip, section);
        this.searchString = searchString;
        this.results = results;
        this.type = type;
    }

    public ServerSearchEvent(MysterAddress ip, int section, String searchString, MysterType type) {
        this(ip, section, searchString, type, null);
    }

    public String getSearchString() {
        return searchString;
    }

    public String[] getResults() {
        return results;
    }

    public MysterType getType() {
        return type;
    }
}
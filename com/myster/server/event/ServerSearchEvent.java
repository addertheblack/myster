/**
 * ...
 */

package com.myster.server.event;

import com.myster.net.MysterAddress;

;

public class ServerSearchEvent extends ServerEvent {
    public final static int REQUESTED = 0;

    public final static int RESULT = 1;

    String searchString, result, type;

    public ServerSearchEvent(int id, MysterAddress ip, int section,
            String searchString, String type, String result) {
        super(id, ip, section);
        this.searchString = searchString;
        this.result = result;
        this.type = type;
    }

    public ServerSearchEvent(int id, MysterAddress ip, int section,
            String searchString, String type) {
        this(id, ip, section, searchString, type, null);
    }

    public String getSearchString() {
        return searchString;
    }

    public String getResult() {
        return result;
    }

    public String getType() {
        return type;
    }
}
/**
 * 
 * Event specifcaly for the connection manager.
 */

package com.myster.server.event;

import com.myster.net.MysterAddress;

public class ConnectionManagerEvent extends ServerEvent {
    private Object object;

    private boolean isDatagram = false;

    public ConnectionManagerEvent(MysterAddress ip, int section, Object d) {
        this(ip, section, d, false);
    }

    public ConnectionManagerEvent(MysterAddress ip, int section, Object d, boolean isDatagram) {
        super(ip, section);
        object = d;
        this.isDatagram = isDatagram;
    }

    public Object getSectionObject() {
        return object;
    }
    
    public boolean isDatagram() {
        return isDatagram;
    }
}
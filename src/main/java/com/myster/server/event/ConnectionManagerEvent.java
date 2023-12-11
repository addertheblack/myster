/**
 * 
 * Event specifcaly for the connection manager.
 */

package com.myster.server.event;

import com.myster.net.MysterAddress;

public class ConnectionManagerEvent extends ServerEvent {
    public static final int SECTIONCONNECT = 0;

    public static final int SECTIONDISCONNECT = 1;

    private Object object;
    
    private boolean isDatagram = false;

    public ConnectionManagerEvent(int id, MysterAddress ip, int section,
            Object d) {
        this(id, ip, section, d, false);
    }
    
    public ConnectionManagerEvent(int id, MysterAddress ip, int section,
            Object d, boolean isDatagram) {
        super(id, ip, section);
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
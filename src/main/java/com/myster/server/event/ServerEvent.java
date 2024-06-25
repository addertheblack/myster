/**
 * Server event. Extend from this.
 * 
 * Methods that consume these events should have ints.
 */

package com.myster.server.event;

import com.myster.net.MysterAddress;

public abstract class ServerEvent  {
    private MysterAddress address;

    private long time;

    private int section;

    public ServerEvent(MysterAddress address, int section) {
        this.address = address;
        this.section = section;
        this.time = System.currentTimeMillis();
    }

    public MysterAddress getAddress() {
        return address;
    }

    public int getSection() {
        return section;
    }

    public long getTimeStamp() {
        return time;
    }
}

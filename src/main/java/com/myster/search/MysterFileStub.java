/*
 * 
 * Title: Myster Open Source Author: Andrew Trumper Description: Generic Myster
 * Code
 * 
 * This code is under GPL
 * 
 * Copyright Andrew Trumper 2000-2005
 */

package com.myster.search;

import com.myster.net.MysterAddress;
import com.myster.type.MysterType;

/**
 * Represents a file on the Myster network. Is immutable.
 */
public final class MysterFileStub {
    private final MysterAddress ip;

    private final MysterType type;

    private final String name;

    public MysterFileStub(MysterAddress ip, MysterType type, String name) {
        this.ip = ip;
        this.type = type;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String getIP() {
        return ip.toString();
    }

    public MysterAddress getMysterAddress() {
        return ip;
    }

    public MysterType getType() {
        return type;
    }

    public String toString() {
        return "Myster File Stub: " + ip + " -> " + type + " -> " + name;
    }
}



package com.myster.net.stream.server;

import com.myster.type.MysterType;

public class NotInitializedException extends RuntimeException {
    public final MysterType mysterType;

    public NotInitializedException(String string, MysterType mysterType) {
        super(string);
        this.mysterType = mysterType;
    }
    
    @Override
    public String toString() {
        return  getMessage() + ":" + mysterType;
    }
}

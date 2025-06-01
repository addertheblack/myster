
package com.myster.server.stream;

import com.myster.type.MysterType;

public class NotInitializedException extends Exception {
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

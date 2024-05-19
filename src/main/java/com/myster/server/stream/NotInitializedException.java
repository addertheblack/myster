
package com.myster.server.stream;

import com.myster.type.MysterType;

public class NotInitializedException extends Exception {
    public final MysterType mysterType;

    public NotInitializedException(MysterType mysterType) {
        this.mysterType = mysterType;
    }
}

/**
 * Encapsulates user level data for use in Instant message events. This is not
 * protocol level, this is here purely to encapsulate usefull information in a
 * single event.
 * <P>
 * This object should be immutable. As a result it should not be possible to use
 * inheritance on this class.
 */

package com.myster.net.datagram.message;

import com.myster.net.MysterAddress;

public final class InstantMessage {
    public final String message;

    public final String quote; //this may be null!

    public final MysterAddress address;

    public InstantMessage(MysterAddress address, String message, String quote) {
        this.address = address;
        this.message = message;
        this.quote = quote;
    }
}
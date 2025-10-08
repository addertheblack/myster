
package com.myster.net.datagram.client;

import com.myster.net.MysterAddress;

public record PingResponse(MysterAddress address, int pingTimeMs) {
    public boolean isTimeout() { return pingTimeMs == -1; }
}
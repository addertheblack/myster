
package com.myster.tracker;

import com.myster.client.datagram.PingResponse;
import com.myster.type.MysterType;

public interface MysterServerListener {
    void listChanged(MysterType type);
    void serverRefresh(MysterServer server);
    void serverPing(PingResponse server);
}

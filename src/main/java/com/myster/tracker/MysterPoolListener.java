
package com.myster.tracker;

import com.myster.client.datagram.PingResponse;

public interface MysterPoolListener {
    void serverRefresh(MysterServer server);
    void serverPing(PingResponse server);
    void deadServer(MysterIdentity identity);
}

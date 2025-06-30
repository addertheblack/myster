
package com.myster.tracker;

import com.myster.client.datagram.PingResponse;

public interface MysterPoolListener {
    void serverRefresh(MysterServer server);
    void serverPing(PingResponse server);
    
    /**
     * Notifies the listener that a server has no more addresses associated with it
     * and should be removed from the list as it is unreachable.
     * 
     * @param identity The identity of the server that is dead.
     */
    void deadServer(MysterIdentity identity);
}

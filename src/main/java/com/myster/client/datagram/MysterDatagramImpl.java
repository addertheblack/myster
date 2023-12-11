
package com.myster.client.datagram;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Future;

import com.general.thread.CallListener;
import com.general.thread.PromiseFuture;
import com.myster.client.net.MysterDatagram;
import com.myster.hash.FileHash;
import com.myster.mml.RobustMML;
import com.myster.net.MysterAddress;
import com.myster.search.MysterFileStub;
import com.myster.type.MysterType;

public class MysterDatagramImpl implements MysterDatagram {
    @Override
    public PromiseFuture<String[]> getTopServers(MysterAddress ip,
                                          MysterType type,
                                          CallListener<String[]> listener) throws IOException {
        return StandardDatagramSuite.getTopServers(ip, type).addCallListener(listener);
    }

    @Override
    public PromiseFuture<List<String>> getSearch(MysterAddress ip,
                                          MysterType type,
                                          String searchString,
                                          CallListener<List<String>> listener)
            throws IOException {
        return StandardDatagramSuite.getSearch(ip, type, searchString).addCallListener(listener);
    }

    @Override
    public PromiseFuture<MysterType[]> getTypes(MysterAddress ip, CallListener<MysterType[]> listener)
            throws IOException {
        return StandardDatagramSuite.getTypes(ip).addCallListener(listener);
    }

    @Override
    public PromiseFuture<RobustMML> getServerStats(MysterAddress ip, CallListener<RobustMML> listener)
            throws IOException {
        return StandardDatagramSuite.getServerStats(ip).addCallListener(listener);
    }

    @Override
    public PromiseFuture<RobustMML> getFileStats(MysterFileStub stub, CallListener<RobustMML> listener)
            throws IOException {
        return StandardDatagramSuite.getFileStats(stub).addCallListener(listener);
    }

    @Override
    public PromiseFuture<String> getFileFromHash(MysterAddress ip,
                                                 MysterType type,
                                                 FileHash hash,
                                                 CallListener<String> listener)
            throws IOException {
        return StandardDatagramSuite.getFileFromHash(ip, type, hash).addCallListener(listener);
    }

    @Override
    public void ping(MysterAddress address, PingEventListener listener)  {
        UDPPingClient.ping(address, listener);
    }
}

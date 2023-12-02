
package com.myster.client.net;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Future;

import com.general.thread.CallListener;
import com.general.thread.PromiseFuture;
import com.myster.client.datagram.PingEventListener;
import com.myster.hash.FileHash;
import com.myster.mml.RobustMML;
import com.myster.net.MysterAddress;
import com.myster.search.MysterFileStub;
import com.myster.type.MysterType;

public interface MysterDatagram {
    public PromiseFuture<String[]> getTopServers(final MysterAddress ip,
                                                 final MysterType type,
                                                 final CallListener<String[]> listener)
            throws IOException;


    public PromiseFuture<List<String>> getSearch(final MysterAddress ip,
                                                 final MysterType type,
                                                 final String searchString,
                                                 final CallListener<List<String>> listener)
            throws IOException;

    public PromiseFuture<MysterType[]> getTypes(final MysterAddress ip,
                                                final CallListener<MysterType[]> listener)
            throws IOException;

    public PromiseFuture<RobustMML> getServerStats(final MysterAddress ip,
                                                   final CallListener<RobustMML> listener)
            throws IOException;

    public PromiseFuture<RobustMML> getFileStats(final MysterFileStub stub,
                                                 final CallListener<RobustMML> listener)
            throws IOException;

    public PromiseFuture<String> getFileFromHash(final MysterAddress ip,
                                                 final MysterType type,
                                                 final FileHash hash,
                                                 final CallListener<String> listener)
            throws IOException;

    public void ping(MysterAddress address, PingEventListener listener);
}
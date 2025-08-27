
package com.myster.client.datagram;

import java.io.IOException;
import java.util.List;

import com.general.thread.PromiseFuture;
import com.myster.client.net.MysterDatagram;
import com.myster.hash.FileHash;
import com.myster.message.ImClient;
import com.myster.message.MessagePacket;
import com.myster.mml.MessagePack;
import com.myster.mml.RobustMML;
import com.myster.net.BadPacketException;
import com.myster.net.DataPacket;
import com.myster.net.MysterAddress;
import com.myster.net.StandardDatagramClientImpl;
import com.myster.net.TimeoutException;
import com.myster.search.MysterFileStub;
import com.myster.transaction.TransactionEvent;
import com.myster.transaction.TransactionListener;
import com.myster.transaction.TransactionManager;
import com.myster.type.MysterType;

public class MysterDatagramImpl implements MysterDatagram {
    private final TransactionManager transactionManager;
    private final UDPPingClient pingClient;

    public MysterDatagramImpl(TransactionManager transactionManager, UDPPingClient pingClient) {
        this.transactionManager = transactionManager;
        this.pingClient = pingClient;
    }
    
    @Override
    public PromiseFuture<PingResponse> ping(MysterAddress address) {
        return pingClient.ping(address).useEdt();
    }
    
    @Override
    public PromiseFuture<String[]> getTopServers(final MysterAddress ip, final MysterType type) {
        return doSection(ip, new TopTenDatagramClient(type));
    }

    @Override
    public PromiseFuture<List<String>> getSearch(final MysterAddress ip,
                                                        final MysterType type,
                                                        final String searchString) {
        return doSection(ip, new SearchDatagramClient(type, searchString));
    }
    
    @Override
    public PromiseFuture<MessagePacket> sendInstantMessage(MysterAddress address,
                                                           String msg,
                                                           String reply) {
        return doSection(address, new ImClient(address, msg, reply));
    }

    @Override
    public PromiseFuture<MysterType[]> getTypes(final MysterAddress ip) {
        return doSection(ip, new TypeDatagramClient());
    }

    @Override
    public PromiseFuture<MessagePack> getServerStats(final MysterAddress ip) {
        return doSection(ip, new ServerStatsDatagramClient());
    }

    @Override
    public PromiseFuture<RobustMML> getFileStats(final MysterFileStub stub) {
        return doSection(stub.getMysterAddress(), new FileStatsDatagramClient(stub));
    }

    @Override
    public PromiseFuture<String> getFileFromHash(final MysterAddress ip,
                                                 final MysterType type,
                                                 final FileHash hash) {
        return doSection(ip, new SearchHashDatagramClient(type, hash));
    }


    private <T> PromiseFuture<T> doSection(final MysterAddress address,
            final StandardDatagramClientImpl<T> impl)  {

        return PromiseFuture.<T>newPromiseFuture((context) -> {
            transactionManager.sendTransaction(new DataPacket() { // inline class
                @Override
                public MysterAddress getAddress() {
                    return address;
                }

                @Override
                public byte[] getData() {
                    return impl.getDataForOutgoingPacket();
                }

                @Override
                public byte[] getBytes() {
                    return getData();
                }
            }, impl.getCode(), new TransactionListener() { // inline class
                public void transactionReply(TransactionEvent e) {
                    if (DatagramUtilities.dealWithError(e.getTransaction(), context))
                        return;

                    try {
                        context.setResult(impl.getObjectFromTransaction(e.getTransaction()));
                    } catch (IOException ex) {
                        context.setException(new BadPacketException(ex.getMessage()));
                    }
                }

                public void transactionTimout(TransactionEvent e) {
                    context.setException(new TimeoutException("Transaction timed out"));
                }

                public void transactionCancelled(TransactionEvent event) {
                    if (!context.isCancelled()) {
                        throw new IllegalStateException("context FuturePromise should already be cancelled");
                    }
                }
            });
        }).useEdt();
    }

}

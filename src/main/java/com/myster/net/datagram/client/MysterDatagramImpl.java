package com.myster.net.datagram.client;

import java.io.IOException;
import java.util.List;

import com.general.thread.PromiseFuture;
import com.myster.hash.FileHash;
import com.myster.mml.MessagePack;
import com.myster.mml.RobustMML;
import com.myster.net.MysterAddress;
import com.myster.net.client.MysterDatagram;
import com.myster.net.client.ParamBuilder;
import com.myster.net.datagram.BadPacketException;
import com.myster.net.datagram.DataPacket;
import com.myster.net.datagram.TimeoutException;
import com.myster.net.datagram.message.ImClient;
import com.myster.net.datagram.message.MessagePacket;
import com.myster.search.MysterFileStub;
import com.myster.transaction.TransactionEvent;
import com.myster.transaction.TransactionListener;
import com.myster.transaction.TransactionManager;
import com.myster.type.MysterType;

/**
 * Implementation of Myster's datagram transactions
 */
public class MysterDatagramImpl implements MysterDatagram {
    private final TransactionManager transactionManager;
    private final UDPPingClient pingClient;
    
    /**
     * This can lookup the public keys for servers based on an address. Default behavior is to try and send encrypted to the specific
     * server we want to send to. 
     */
    private final PublicKeyLookup lookup;
    
    public MysterDatagramImpl(TransactionManager transactionManager, UDPPingClient pingClient, PublicKeyLookup lookup) {
        this.transactionManager = transactionManager;
        this.pingClient = pingClient;
        this.lookup = lookup;
    }
    
    @Override
    public PromiseFuture<PingResponse> ping(ParamBuilder params) {
        MysterAddress address = extractAddress(params);
        return pingClient.ping(address).useEdt();
    }
    
    @Override
    public PromiseFuture<String[]> getTopServers(final ParamBuilder params, final MysterType type) {
        return doSection(params, new TopTenDatagramClient(type));
    }

    @Override
    public PromiseFuture<List<String>> getSearch(final ParamBuilder params,
                                                        final MysterType type,
                                                        final String searchString) {
        return doSection(params, new SearchDatagramClient(type, searchString));
    }
    
    @Override
    public PromiseFuture<MessagePacket> sendInstantMessage(ParamBuilder params,
                                                           String msg,
                                                           String reply) {
        MysterAddress address = extractAddress(params);
        return doSection(params, new ImClient(address, msg, reply));
    }

    @Override
    public PromiseFuture<MysterType[]> getTypes(final ParamBuilder params) {
        return doSection(params, new TypeDatagramClient());
    }

    @Override
    public PromiseFuture<MessagePack> getServerStats(final ParamBuilder params) {
        return doSection(params, new ServerStatsDatagramClient());
    }

    @Override
    public PromiseFuture<RobustMML> getFileStats(final MysterFileStub stub) {
        return doSection(new ParamBuilder(stub.getMysterAddress()), new FileStatsDatagramClient(stub));
    }

    @Override
    public PromiseFuture<String> getFileFromHash(final ParamBuilder params,
                                                 final MysterType type,
                                                 final FileHash hash) {
        return doSection(params, new SearchHashDatagramClient(type, hash));
    }

    /**
     * Extract MysterAddress from ParamBuilder. For now, just handles the address case.
     * TODO: Add support for identity lookup and encryption options later.
     */
    private static MysterAddress extractAddress(ParamBuilder params) {
        // For now, just extract the address - we'll add identity and encryption support later
        return params.getAddress().orElseThrow(() -> 
            new IllegalArgumentException("ParamBuilder must contain an address"));
    }

    private <T> PromiseFuture<T> doSection(final ParamBuilder params,
                                           final StandardDatagramClientImpl<T> impl) {
        final MysterAddress address = extractAddress(params);
        
        if (params.isForceUnencrypted()) {
            throw new IllegalStateException("Not implemented yet");
        }

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
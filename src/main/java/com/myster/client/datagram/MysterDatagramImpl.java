package com.myster.client.datagram;

import java.io.IOException;
import java.security.PublicKey;
import java.util.List;
import java.util.Optional;

import com.general.thread.PromiseFuture;
import com.myster.client.net.MysterDatagram;
import com.myster.client.net.ParamBuilder;
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
import com.myster.tracker.MysterIdentity;
import com.myster.transaction.TransactionEvent;
import com.myster.transaction.TransactionListener;
import com.myster.transaction.TransactionManager;
import com.myster.type.MysterType;

/**
 * Used to lookup the server PublicKey given an Identity, MysterAddress
 */
interface PublicKeyLookup {
    /**
     * @param identity to convert to a public key
     * @return the public key for this identity or empty if not found
     */
    Optional<PublicKey> convert(MysterIdentity identity);
    Optional<PublicKey> getCached(MysterAddress address);
    PromiseFuture<Optional<PublicKey>> fetchPublicKey(MysterAddress address);
}

interface AddressLookup {
    Optional<MysterAddress> findAddress(MysterIdentity identity); 
}


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
    
    /**
     * In case we've specified a server via its identity
     */
    private final AddressLookup addressLookup;

    public MysterDatagramImpl(TransactionManager transactionManager, UDPPingClient pingClient, PublicKeyLookup lookup, AddressLookup addressLookup) {
        this.transactionManager = transactionManager;
        this.pingClient = pingClient;
        this.lookup = lookup;
        this.addressLookup = addressLookup;
    }
    
    @Override
    public PromiseFuture<PingResponse> ping(ParamBuilder params) {
        MysterAddress address = extractAddress(params);
        return pingClient.ping(address).useEdt();
    }
    
    @Override
    public PromiseFuture<String[]> getTopServers(final ParamBuilder params, final MysterType type) {
        MysterAddress address = extractAddress(params);
        return doSection(address, new TopTenDatagramClient(type));
    }

    @Override
    public PromiseFuture<List<String>> getSearch(final ParamBuilder params,
                                                        final MysterType type,
                                                        final String searchString) {
        MysterAddress address = extractAddress(params);
        return doSection(address, new SearchDatagramClient(type, searchString));
    }
    
    @Override
    public PromiseFuture<MessagePacket> sendInstantMessage(ParamBuilder params,
                                                           String msg,
                                                           String reply) {
        MysterAddress address = extractAddress(params);
        return doSection(address, new ImClient(address, msg, reply));
    }

    @Override
    public PromiseFuture<MysterType[]> getTypes(final ParamBuilder params) {
        MysterAddress address = extractAddress(params);
        return doSection(address, new TypeDatagramClient());
    }

    @Override
    public PromiseFuture<MessagePack> getServerStats(final ParamBuilder params) {
        MysterAddress address = extractAddress(params);
        return doSection(address, new ServerStatsDatagramClient());
    }

    @Override
    public PromiseFuture<RobustMML> getFileStats(final MysterFileStub stub) {
        return doSection(stub.getMysterAddress(), new FileStatsDatagramClient(stub));
    }

    @Override
    public PromiseFuture<String> getFileFromHash(final ParamBuilder params,
                                                 final MysterType type,
                                                 final FileHash hash) {
        MysterAddress address = extractAddress(params);
        return doSection(address, new SearchHashDatagramClient(type, hash));
    }

    /**
     * Extract MysterAddress from ParamBuilder. For now, just handles the address case.
     * TODO: Add support for identity lookup and encryption options later.
     */
    private MysterAddress extractAddress(ParamBuilder params) {
        // For now, just extract the address - we'll add identity and encryption support later
        return params.getAddress().orElseThrow(() -> 
            new IllegalArgumentException("ParamBuilder must contain an address"));
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
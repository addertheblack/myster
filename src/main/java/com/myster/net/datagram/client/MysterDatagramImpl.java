package com.myster.net.datagram.client;

import java.io.IOException;
import java.security.PublicKey;
import java.util.List;
import java.util.Optional;

import com.general.thread.PromiseFuture;
import com.myster.hash.FileHash;
import com.myster.identity.Identity;
import com.myster.mml.MessagePak;
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
 * Implementation of Myster's datagram transactions with MSD encryption support
 * Default behavior: Encrypt by default (unsigned if no client identity available)
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
    public PromiseFuture<MessagePak> getServerStats(final ParamBuilder params) {
        return doSection(params, new ServerStatsDatagramClient());
    }

    @Override
    public PromiseFuture<MessagePak> getFileStats(final MysterFileStub stub) {
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
        
        StandardDatagramClientImpl<T> actualImpl;
        
        if (params.isForceUnencrypted()) {
            // Explicitly requested unencrypted legacy protocol
            actualImpl = impl;
        } else if (params.isForceEncryption()) {
            // Explicitly requested MSD encrypted protocol
            Optional<PublicKey> serverPublicKey = lookup.getServerPublicKey(address);
            if (serverPublicKey.isEmpty()) {
                return PromiseFuture.newPromiseFutureException(
                    new IllegalStateException("No server public key available for encryption"));
            }
            Optional<Identity> clientIdentity = getClientIdentity(params);
            actualImpl = new EncryptingStandardDatagramClientImpl<>(impl, serverPublicKey.get(), clientIdentity);
        } else {
            // Default behavior - encrypt by default if server public key is available
            if (shouldUseEncryption(params)) {
                Optional<PublicKey> serverPublicKey = lookup.getServerPublicKey(address);
                Optional<Identity> clientIdentity = getClientIdentity(params);
                actualImpl = new EncryptingStandardDatagramClientImpl<>(impl, serverPublicKey.get(), clientIdentity);
            } else {
                // Fall back to unencrypted if no server public key available
                actualImpl = impl;
            }
        }
        
        // Use the simple unencrypted sending logic for both cases - the decorator handles encryption transparently
        return sendPacket(address, actualImpl);
    }
    
    private <T> PromiseFuture<T> sendPacket(MysterAddress address, StandardDatagramClientImpl<T> impl) {
        return PromiseFuture.<T>newPromiseFuture((context) -> {
            transactionManager.sendTransaction(
                new SimpleDataPacket(address, impl.getDataForOutgoingPacket()),
                impl.getCode(),  // This will be STLS_CODE for encrypted packets, original code for unencrypted
                new TransactionListener() {
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
    
    /**
     * Implements DataPacket with a MysterAddress and a byte[] payload for cases where we don't have a header.
     */
    private static class SimpleDataPacket implements DataPacket {
        private final MysterAddress address;
        private final byte[] data;
        
        public SimpleDataPacket(MysterAddress address, byte[] data) {
            this.address = address;
            this.data = data;
        }
        
        @Override
        public MysterAddress getAddress() {
            return address;
        }

        @Override
        public byte[] getData() {
            return data;
        }
    }
    
    private boolean shouldUseEncryption(ParamBuilder params) {
        // Default policy: use encryption if server public key is available
        MysterAddress address = extractAddress(params);
        return lookup.getServerPublicKey(address).isPresent();
    }
    
    private Optional<Identity> getClientIdentity(ParamBuilder params) {
        // Try to get identity from params, or use default client identity
        if (params.getIdentity().isPresent()) {
            // Convert MysterIdentity to Identity if needed
            // This depends on the relationship between these classes
            return Optional.empty(); // TODO: Implement conversion
        }
        
        // Use default client identity if available
        return lookup.getDefaultClientIdentity();
    }
}
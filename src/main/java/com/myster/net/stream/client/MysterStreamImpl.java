package com.myster.net.stream.client;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.security.PublicKey;
import java.util.List;
import java.util.Optional;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import com.myster.access.AccessList;
import com.myster.access.AccessListGetClient;
import com.myster.hash.FileHash;
import com.myster.mml.MessagePak;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.net.client.MysterStream;
import com.myster.net.client.ParamBuilder;
import com.myster.net.stream.client.msdownload.MSDownloadLocalQueue;
import com.myster.net.stream.client.msdownload.MSDownloadParams;
import com.myster.search.MysterFileStub;
import com.myster.type.MysterType;
import com.myster.type.join.TypeJoinStatus;

/**
 * Blocking stream facade with cache-first identity discovery on the caller-owned TLS connection.
 * The transport sets connect/read timeouts before discovery; individual sections retain them.
 */
public class MysterStreamImpl implements MysterStream {
    private static final int MAX_DISCOVERED_KEYS = 512;

    private final MSDownloadLocalQueue downloadQueue;
    private final Function<MysterAddress, Optional<PublicKey>> cachedKeys;
    private final Map<MysterAddress, PublicKey> discoveredKeys = new LinkedHashMap<>(16, .75f, true);

    /** Shares the existing server-key cache; discovery itself uses the socket's stats section. */
    public MysterStreamImpl(MSDownloadLocalQueue downloadQueue,
                            Function<MysterAddress, Optional<PublicKey>> cachedKeys) {
        this.downloadQueue = downloadQueue;
        this.cachedKeys = java.util.Objects.requireNonNull(cachedKeys, "cachedKeys");
    }

    @Override
    public MysterSocket makeStreamConnection(ParamBuilder params) throws IOException {
        java.util.Objects.requireNonNull(params, "params");
        MysterAddress address = params.getAddress().orElseThrow(() ->
                new IllegalArgumentException("ParamBuilder must contain an address"));

        Optional<PublicKey> key = params.getExpectedServerPublicKey()
                .or(() -> findCachedServerKey(address));

        MysterSocket socket = key.isPresent()
                ? MysterSocketFactory.makeStreamConnection(address, key.orElseThrow())
                : MysterSocketFactory.makeStreamConnection(address);

        boolean handedOff = false;

        try {
            PublicKey verifiedKey;
            if (key.isPresent()) {
                verifiedKey = key.get();
            } else {
                verifiedKey = downloadKeyFromServer(socket);
            }

            synchronized (discoveredKeys) {
                discoveredKeys.put(address, verifiedKey);
                while (discoveredKeys.size() > MAX_DISCOVERED_KEYS) {
                    discoveredKeys.remove(discoveredKeys.keySet().iterator().next());
                }
            }

            handedOff = true;

            return socket;
        } finally {
            if (!handedOff) {
                socket.close();
            }
        }
    }

    private PublicKey downloadKeyFromServer(MysterSocket socket) throws IOException {
        MessagePak stats = getServerStats(socket);
        PublicKey verifiedKey = stats.getByteArray(com.myster.net.stream.server.ServerStats.IDENTITY)
                .flatMap(com.myster.identity.Util::publicKeyFromBytes)
                .orElseThrow(() -> new IOException("Server stats did not contain a public key"));
        PublicKey peerKey = socket.getAuthenticatedPeerKey()
                                  .orElseThrow(() -> new IOException("Connection has no authenticated peer key"));
        if (!verifiedKey.equals(peerKey)) {
            throw new IOException("Server stats identity does not match the TLS peer");
        }

        return verifiedKey;
    }

    private Optional<PublicKey> findCachedServerKey(MysterAddress address) {
        synchronized (discoveredKeys) {
            PublicKey key = discoveredKeys.get(address);
            if (key != null) {
                return Optional.of(key);
            }
        }
        return cachedKeys.apply(address);
    }
    
    @Override
    public List<String> getSearch(MysterSocket socket, MysterType searchType, String searchString)
            throws IOException {
        return StandardSuiteStream.getSearch(socket, searchType, searchString);
    }

    @Override
    public List<String> getTopServers(MysterSocket socket, MysterType searchType)
            throws IOException {
        return StandardSuiteStream.getTopServers(socket, searchType);
    }

    @Override
    public MysterType[] getTypes(MysterSocket socket) throws IOException {
        return StandardSuiteStream.getTypes(socket);
    }

    @Override
    public MessagePak getServerStats(MysterSocket socket) throws IOException {
        return StandardSuiteStream.getServerStats(socket);
    }
    
    @Override
    public String getFileFromHash(MysterSocket socket, MysterType type, FileHash[] hashes)
            throws IOException {
        return StandardSuiteStream.getFileFromHash(socket, type, hashes);
    }

    @Override
    public MessagePak getFileStats(MysterSocket socket, MysterFileStub stub) throws IOException {
        return StandardSuiteStream.getFileStats(socket, stub);
    }

    @Override
    public BufferedImage getThumbnail(MysterSocket socket, MysterType type, String filename, int size)
            throws IOException {
        return StandardSuiteStream.getThumbnail(socket, type, filename, size);
    }

    @Override
    public void downloadFile(MSDownloadParams p) {
        StandardSuiteStream.downloadFile(p, downloadQueue, this);
    }

    @Override
    public boolean ping(MysterSocket socket) {
        return StandardSuiteStream.ping(socket);
    }

    @Override
    public Optional<AccessList> getAccessList(MysterSocket socket, MysterType type)
            throws IOException {
        return AccessListGetClient.fetchAccessList(socket, type);
    }

    @Override
    public TypeJoinStatus redeemTypeInvitation(MysterSocket socket, MysterType type,
            byte[] invitationId, String code) throws IOException {
        return TypeJoinClient.redeem(socket, type, invitationId, code);
    }
}

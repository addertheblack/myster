package com.myster.net.stream.client;

import java.io.IOException;
import java.security.PublicKey;
import java.util.List;
import java.util.Optional;

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

public class MysterStreamImpl implements MysterStream {
    private final MSDownloadLocalQueue downloadQueue;
    private final ConnectionFactory connectionFactory;

    public MysterStreamImpl(MSDownloadLocalQueue downloadQueue) {
        this(downloadQueue, MysterStreamImpl::openConnection);
    }

    MysterStreamImpl(MSDownloadLocalQueue downloadQueue, ConnectionFactory connectionFactory) {
        this.downloadQueue = downloadQueue;
        this.connectionFactory = java.util.Objects.requireNonNull(
                connectionFactory, "connectionFactory");
    }
    
    @Override
    public MysterSocket makeStreamConnection(ParamBuilder params) throws IOException {
        java.util.Objects.requireNonNull(params, "params");
        MysterAddress address = params.getAddress().orElseThrow(() ->
                new IllegalArgumentException("ParamBuilder must contain an address"));
        return connectionFactory.open(address, params.getExpectedServerPublicKey());
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
    public void downloadFile(MSDownloadParams p) {
        StandardSuiteStream.downloadFile(p, downloadQueue);
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

    private static MysterSocket openConnection(MysterAddress address,
            Optional<PublicKey> expectedServerPublicKey) throws IOException {
        if (expectedServerPublicKey.isPresent()) {
            return MysterSocketFactory.makeStreamConnection(
                    address, expectedServerPublicKey.orElseThrow());
        }
        return MysterSocketFactory.makeStreamConnection(address);
    }

    @FunctionalInterface
    interface ConnectionFactory {
        MysterSocket open(MysterAddress address, Optional<PublicKey> expectedServerPublicKey)
                throws IOException;
    }
}

package com.myster.net.stream.client;

import java.io.IOException;
import java.util.List;

import com.myster.hash.FileHash;
import com.myster.mml.MessagePak;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.net.client.MysterStream;
import com.myster.net.stream.client.msdownload.MSDownloadLocalQueue;
import com.myster.net.stream.client.msdownload.MSDownloadParams;
import com.myster.search.MysterFileStub;
import com.myster.type.MysterType;

public class MysterStreamImpl implements MysterStream {
    private final MSDownloadLocalQueue downloadQueue;

    public MysterStreamImpl(MSDownloadLocalQueue downloadQueue) {
        this.downloadQueue = downloadQueue;
    }
    
    @Override
    public MysterSocket makeStreamConnection(MysterAddress ip) throws IOException {
        return MysterSocketFactory.makeStreamConnection(ip);
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
}
package com.myster.net.stream.client;

import java.io.IOException;
import java.util.List;

import com.myster.hash.FileHash;
import com.myster.mml.MessagePack;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.net.client.MysterStream;
import com.myster.search.HashCrawlerManager;
import com.myster.search.MysterFileStub;
import com.myster.type.MysterType;
import com.myster.ui.MysterFrameContext;

public class MysterStreamImpl implements MysterStream {
    @Override
    public MysterSocket makeStreamConnection(MysterAddress ip) throws IOException {
        return MysterSocketFactory.makeStreamConnection(ip);
    }
    
    @Override
    public List<String> getSearch(MysterSocket socket, MysterType searchType, String searchString)
            throws IOException {
        return StandardSuite.getSearch(socket, searchType, searchString);
    }

    @Override
    public List<String> getTopServers(MysterSocket socket, MysterType searchType)
            throws IOException {
        return StandardSuite.getTopServers(socket, searchType);
    }

    @Override
    public MysterType[] getTypes(MysterSocket socket) throws IOException {
        return StandardSuite.getTypes(socket);
    }

    @Override
    public MessagePack getServerStats(MysterSocket socket) throws IOException {
        return StandardSuite.getServerStats(socket);
    }
    
    @Override
    public String getFileFromHash(MysterSocket socket, MysterType type, FileHash[] hashes)
            throws IOException {
        return StandardSuite.getFileFromHash(socket, type, hashes);
    }

    @Override
    public MessagePack getFileStats(MysterSocket socket, MysterFileStub stub) throws IOException {
        return StandardSuite.getFileStats(socket, stub);
    }

    @Override
    public void downloadFile(MysterFrameContext c,
                            HashCrawlerManager crawlerManager,
                            MysterAddress ip,
                            MysterFileStub stub) {
        StandardSuite.downloadFile(c, crawlerManager, ip, stub);
    }
}
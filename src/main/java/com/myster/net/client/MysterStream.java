
package com.myster.net.client;

import java.io.IOException;
import java.util.List;

import com.myster.hash.FileHash;
import com.myster.mml.RobustMML;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.net.stream.client.MysterSocketFactory;
import com.myster.search.HashCrawlerManager;
import com.myster.search.MysterFileStub;
import com.myster.type.MysterType;
import com.myster.ui.MysterFrameContext;

public interface MysterStream {
    /**
     * Create a new connection. This is what you call first
     */
    MysterSocket makeStreamConnection(MysterAddress ip) throws IOException;
    
    // Vector of strings
    List<String> getSearch(MysterSocket socket, MysterType searchType, String searchString)
            throws IOException;

    List<String> getTopServers(MysterSocket socket, MysterType searchType)
            throws IOException;

    MysterType[] getTypes(MysterSocket socket) throws IOException;

    RobustMML getServerStats(MysterSocket socket) throws IOException;
    
    String getFileFromHash(MysterSocket socket, MysterType type, FileHash[] hashes) throws IOException;
    RobustMML getFileStats(MysterSocket socket, MysterFileStub stub)
            throws IOException;

    /**
     * downloadFile downloads a file by starting up a MultiSourceDownload or
     * Regular old style download whichever is appropriate.
     * <p>
     * THIS ROUTINE IS ASYNCHRONOUS!
     */
    void downloadFile(MysterFrameContext c, final HashCrawlerManager crawlerManager, final MysterAddress ip, final MysterFileStub stub);
    
    default <T> T doSection(MysterAddress ip, StandardStreamSection<T> section) throws IOException {
        try (MysterSocket socket = MysterSocketFactory.makeStreamConnection(ip)){
            return section.doSection(socket);
        }
    }
}
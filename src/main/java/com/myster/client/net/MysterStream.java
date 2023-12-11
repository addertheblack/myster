
package com.myster.client.net;

import java.io.IOException;
import java.util.List;

import com.myster.hash.FileHash;
import com.myster.mml.RobustMML;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.net.MysterSocketFactory;
import com.myster.search.HashCrawlerManager;
import com.myster.search.MysterFileStub;
import com.myster.type.MysterType;
import com.myster.ui.MysterFrameContext;

public interface MysterStream {
    // Vector of strings
    public List<String> getSearch(MysterSocket socket, MysterType searchType, String searchString)
            throws IOException;

    public List<String> getTopServers(MysterSocket socket, MysterType searchType)
            throws IOException;

    public MysterType[] getTypes(MysterSocket socket) throws IOException;

    public RobustMML getServerStats(MysterSocket socket) throws IOException;
    
    public String getFileFromHash(MysterSocket socket, MysterType type, FileHash[] hashes) throws IOException;
    public RobustMML getFileStats(MysterSocket socket, MysterFileStub stub)
            throws IOException;

    /**
     * downloadFile downloads a file by starting up a MultiSourceDownload or
     * Regular old style download whichever is appropriate.
     * <p>
     * THIS ROUTINE IS ASYNCHRONOUS!
     */
    public void downloadFile(MysterFrameContext c, final HashCrawlerManager crawlerManager, final MysterAddress ip, final MysterFileStub stub);
    
    public default <T> T byIp(MysterAddress ip, StandardStreamSection<T> section) throws IOException {
        try (MysterSocket socket = MysterSocketFactory.makeStreamConnection(ip)){
            return section.doSection(socket);
        }
    }
}
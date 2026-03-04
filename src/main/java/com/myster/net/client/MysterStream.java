
package com.myster.net.client;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import com.myster.access.AccessList;
import com.myster.hash.FileHash;
import com.myster.mml.MessagePak;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.net.stream.client.MysterSocketFactory;
import com.myster.net.stream.client.msdownload.MSDownloadParams;
import com.myster.search.MysterFileStub;
import com.myster.type.MysterType;

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

    MessagePak getServerStats(MysterSocket socket) throws IOException;
    
    String getFileFromHash(MysterSocket socket, MysterType type, FileHash[] hashes) throws IOException;
    MessagePak getFileStats(MysterSocket socket, MysterFileStub stub)
            throws IOException;
    
    boolean ping(MysterSocket socket);

    /**
     * Fetches the access list for the given type from the specified server.
     *
     * <p>Unlike other {@code MysterStream} methods, this opens its own fresh TCP connection
     * internally (via {@link com.myster.access.AccessListGetClient}) rather than accepting an
     * existing {@link MysterSocket}. The access list fetch is a separate, independent protocol
     * transaction (section 125), not piggybacked on any other connection.
     *
     * <p>This is a plain blocking call. Callers that need async behaviour should wrap with
     * {@link com.general.thread.PromiseFutures#execute}.
     *
     * @param server     the server to fetch from
     * @param type       the type whose access list is requested
     * @return the AccessList, or empty if the server has none for this type
     * @throws IOException if the connection or protocol exchange fails
     */
    Optional<AccessList> getAccessList(MysterAddress server, MysterType type) throws IOException;

    /**
     * downloadFile downloads a file by starting up a MultiSourceDownload or
     * Regular old style download whichever is appropriate.
     * <p>
     * THIS ROUTINE IS ASYNCHRONOUS!
     */
    void downloadFile(MSDownloadParams p);
    
    default <T> T doSection(MysterAddress ip, StandardStreamSection<T> section) throws IOException {
        try (MysterSocket socket = MysterSocketFactory.makeStreamConnection(ip)){
            return section.doSection(socket);
        }
    }
}
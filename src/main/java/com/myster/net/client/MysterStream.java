
package com.myster.net.client;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

import com.myster.access.AccessList;
import com.myster.hash.FileHash;
import com.myster.mml.MessagePak;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.net.stream.client.msdownload.MSDownloadParams;
import com.myster.search.MysterFileStub;
import com.myster.type.MysterType;
import com.myster.type.join.TypeJoinStatus;

public interface MysterStream {
    /**
     * Creates a stream connection using common transport parameters.
     *
     * <p>An expected server public key requires the TLS certificate presented by the remote server
     * to contain that key. Stream connections are TLS regardless of the datagram encryption flags
     * in {@link ParamBuilder}.
     *
     * @param params target address and optional expected server public key
     * @return a connected, caller-owned socket, never null
     * @throws IOException if the connection or TLS authentication fails
     * @throws IllegalArgumentException if no target address is present
     */
    MysterSocket makeStreamConnection(ParamBuilder params) throws IOException;

    /** Creates a stream connection without an independently supplied expected server key. */
    default MysterSocket makeStreamConnection(MysterAddress ip) throws IOException {
        return makeStreamConnection(new ParamBuilder(ip));
    }
    
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

    /**
     * Fetches one thumbnail through TCP section 79. This is a blocking call for worker threads;
     * callers choose background execution and must sequence operations on the socket.
     *
     * @param socket caller-owned connection, left open after success or a thumbnail miss
     * @param type file's Myster type
     * @param filename exact opaque file reference returned by listing/search
     * @param size maximum width and height, from 1 through 256 pixels; images are not padded
     * @return caller-owned image, or null for denied, missing or unavailable thumbnails
     * @throws IllegalArgumentException for invalid request arguments or an oversized request
     * @throws com.myster.net.stream.client.UnknownProtocolException if the peer rejects the section
     * @throws IOException for malformed responses or I/O failure; close/discard the connection
     */
    BufferedImage getThumbnail(MysterSocket socket, MysterType type, String filename, int size)
            throws IOException;
    
    boolean ping(MysterSocket socket);

    /** Runs stream section 125 on a caller-owned socket, leaving it open for another section. */
    Optional<AccessList> getAccessList(MysterSocket socket, MysterType type) throws IOException;

    /** Opens a connection, runs stream section 125, and closes the connection. */
    default Optional<AccessList> getAccessList(MysterAddress server, MysterType type)
            throws IOException {
        try (MysterSocket socket = makeStreamConnection(server)) {
            return getAccessList(socket, type);
        }
    }

    /**
     * Runs authenticated stream section 126 on a caller-owned socket, leaving it open so the caller
     * can obtain the resulting signed access list through section 125.
     */
    TypeJoinStatus redeemTypeInvitation(MysterSocket socket, MysterType type, byte[] invitationId,
            String code) throws IOException;

    /**
     * downloadFile downloads a file by starting up a MultiSourceDownload or
     * Regular old style download whichever is appropriate.
     * <p>
     * THIS ROUTINE IS ASYNCHRONOUS!
     */
    void downloadFile(MSDownloadParams p);
    
    default <T> T doSection(ParamBuilder params, StandardStreamSection<T> section)
            throws IOException {
        try (MysterSocket socket = makeStreamConnection(params)) {
            return section.doSection(socket);
        }
    }
}

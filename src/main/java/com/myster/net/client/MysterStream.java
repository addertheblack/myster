
package com.myster.net.client;

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
     * @return a connected, caller-owned socket
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

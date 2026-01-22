package com.myster.net.datagram.client;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.logging.Logger;

import com.myster.filemanager.FileTypeListManager;
import com.myster.identity.Identity;
import com.myster.mml.MessagePak;
import com.myster.net.datagram.DatagramConstants;
import com.myster.net.stream.client.MysterDataInputStream;
import com.myster.net.stream.client.MysterDataOutputStream;
import com.myster.net.stream.server.NotInitializedException;
import com.myster.net.stream.server.ServerStats;
import com.myster.transaction.Transaction;

/**
 * Client-side implementation of bidirectional server stats exchange.
 *
 * <p>This client sends our server stats in the request payload and receives
 * the remote server's stats in the response payload. This allows both parties
 * to learn about each other in a single round-trip transaction.
 *
 * <p>The primary use case is NAT traversal: when we connect to a remote server,
 * we can advertise our actual server port (which may differ from our UDP source
 * port due to NAT), and simultaneously learn about the remote server's stats.
 *
 * <p>This is the client-side counterpart to {@link com.myster.net.server.datagram.BidirectionalServerStatsDatagramServer}.
 */
public class BidirectionalServerStatsDatagramClient implements StandardDatagramClientImpl<MessagePak> {
    private static final Logger log = Logger.getLogger(BidirectionalServerStatsDatagramClient.class.getName());

    private final String serverName;
    private final int port;
    private final Identity identity;
    private final FileTypeListManager fileManager;

    /**
     * Creates a new bidirectional server stats client.
     *
     * @param serverName our server's name
     * @param port our server's port
     * @param identity our server's identity
     * @param fileManager file manager for generating file statistics
     */
    public BidirectionalServerStatsDatagramClient(String serverName,
                                                   int port,
                                                   Identity identity,
                                                   FileTypeListManager fileManager) {
        this.serverName = serverName;
        this.port = port;
        this.identity = identity;
        this.fileManager = fileManager;
    }

    @Override
    public int getCode() {
        return DatagramConstants.BIDIRECTIONAL_SERVER_STATS_TRANSACTION_CODE;
    }

    /**
     * Generates the outgoing packet data containing our server stats.
     *
     * <p>If the file manager is not initialized, sends minimal stats containing
     * only our port. If serialization fails entirely, returns an empty array.
     *
     * @return serialized MessagePak containing our server stats
     */
    @Override
    public byte[] getDataForOutgoingPacket() {
        try {
            MessagePak ourStats = ServerStats.getServerStatsMessagePack(
                serverName, port, identity, fileManager
            );
            ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
            try (var out = new MysterDataOutputStream(byteOut)) {
                out.writeMessagePack(ourStats);
            }
            return byteOut.toByteArray();
        } catch (NotInitializedException e) {
            log.warning("File manager not initialized, sending minimal stats");
            // Send minimal stats with just port
            try {
                MessagePak minimalStats = MessagePak.newEmpty();
                minimalStats.putInt(ServerStats.PORT, port);
                if (serverName != null && !serverName.isEmpty()) {
                    minimalStats.putString(ServerStats.SERVER_NAME, serverName);
                }
                ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
                try (var out = new MysterDataOutputStream(byteOut)) {
                    out.writeMessagePack(minimalStats);
                }
                return byteOut.toByteArray();
            } catch (IOException e2) {
                log.severe("Failed to create minimal stats: " + e2.getMessage());
                return new byte[0];
            }
        } catch (IOException e) {
            log.severe("Failed to serialize stats: " + e.getMessage());
            return new byte[0];
        }
    }

    /**
     * Parses the remote server's stats from the transaction response.
     *
     * @param transaction the transaction response containing the remote server's stats
     * @return MessagePak containing the remote server's stats
     * @throws IOException if deserialization fails
     */
    @Override
    public MessagePak getObjectFromTransaction(Transaction transaction) throws IOException {
        try (MysterDataInputStream in =
                new MysterDataInputStream(new ByteArrayInputStream(transaction.getData()))) {
            return in.readMessagePack();
        }
    }
}


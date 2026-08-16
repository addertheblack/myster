package com.myster.net.datagram.client;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

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
     * <p>The caller must ensure all shared file lists are initialized before
     * selecting transaction {@code 102}. Serialization failures abort the
     * datagram call instead of emitting an invalid payload.
     *
     * @return serialized MessagePak containing our server stats
     * @throws IOException if the business card cannot be serialized
     * @throws IllegalStateException if transaction {@code 102} was selected
     *         before all shared file lists were initialized
     */
    @Override
    public byte[] getDataForOutgoingPacket() throws IOException {
        try {
            return serialize(ServerStats.getServerStatsMessagePack(
                    serverName, port, identity, fileManager));
        } catch (NotInitializedException exception) {
            throw new IllegalStateException(
                    "Transaction 102 requires initialized shared file lists", exception);
        }
    }

    private static byte[] serialize(MessagePak serverStats) throws IOException {
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        try (var out = new MysterDataOutputStream(byteOut)) {
            out.writeMessagePack(serverStats);
        }
        return byteOut.toByteArray();
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

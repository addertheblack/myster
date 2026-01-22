package com.myster.net.server.datagram;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Logger;

import com.myster.filemanager.FileTypeListManager;
import com.myster.identity.Identity;
import com.myster.mml.MessagePak;
import com.myster.net.MysterAddress;
import com.myster.net.datagram.BadPacketException;
import com.myster.net.datagram.DatagramConstants;
import com.myster.net.stream.client.MysterDataInputStream;
import com.myster.net.stream.client.MysterDataOutputStream;
import com.myster.net.stream.server.NotInitializedException;
import com.myster.net.stream.server.ServerStats;
import com.myster.tracker.MysterServerPool;
import com.myster.transaction.Transaction;
import com.myster.transaction.TransactionProtocol;
import com.myster.transaction.TransactionSender;

/**
 * Server side datagram implementation of bidirectional server stats exchange.
 *
 * <p>Unlike the one-way {@link ServerStatsDatagramServer}, this implementation
 * receives the client's server stats in the request payload and returns our own
 * stats in the response. This allows both parties to learn about each other in
 * a single round-trip transaction.
 *
 * <p>When a bidirectional exchange is received:
 * <ol>
 *   <li>Parse the client's server stats from the request</li>
 *   <li>Extract the client's advertised port and identity</li>
 *   <li>Register the client in our server pool via {@link MysterServerPool#suggestAddress}</li>
 *   <li>Generate and return our own server stats in the response</li>
 * </ol>
 *
 * <p>This is particularly useful for NAT traversal scenarios where the client's
 * actual server port may differ from their source port in the UDP packet.
 */
public class BidirectionalServerStatsDatagramServer implements TransactionProtocol {
    private static final Logger log = Logger.getLogger(BidirectionalServerStatsDatagramServer.class.getName());

    private final Supplier<String> getServerName;
    private final Supplier<Integer> getPort;
    private final Identity identity;
    private final FileTypeListManager fileManager;
    private final MysterServerPool pool;

    /**
     * Creates a new bidirectional server stats datagram server.
     *
     * @param getServerName supplier for our server's name
     * @param getPort supplier for our server's port
     * @param identity our server's identity
     * @param fileManager file manager for generating file statistics
     * @param pool server pool to register discovered clients
     */
    public BidirectionalServerStatsDatagramServer(Supplier<String> getServerName,
                                                   Supplier<Integer> getPort,
                                                   Identity identity,
                                                   FileTypeListManager fileManager,
                                                   MysterServerPool pool) {
        this.getServerName = getServerName;
        this.getPort = getPort;
        this.identity = identity;
        this.fileManager = fileManager;
        this.pool = pool;
    }

    @Override
    public int getTransactionCode() {
        return DatagramConstants.BIDIRECTIONAL_SERVER_STATS_TRANSACTION_CODE;
    }

    @Override
    public void transactionReceived(TransactionSender sender,
                                    Transaction transaction,
                                    Object transactionObject)
            throws BadPacketException {
        try (var in = new MysterDataInputStream(new ByteArrayInputStream(transaction.getData()))) {
            MessagePak clientStats = in.readMessagePack();

            MysterAddress senderAddress = transaction.getAddress();
            Optional<Integer> advertisedPort = clientStats.getInt(ServerStats.PORT);

            MysterAddress correctedAddress;
            if (advertisedPort.isPresent()) {
                correctedAddress = new MysterAddress(
                    senderAddress.getInetAddress(),
                    advertisedPort.get()
                );
            } else {
                // Fallback: use sender's source address
                correctedAddress = senderAddress;
            }

            // Add client to our pool
            pool.suggestAddress(correctedAddress);

            log.fine("Received bidirectional server stats from " + correctedAddress);

            // Generate our server stats response
            ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
            try (var out = new MysterDataOutputStream(byteOutputStream)) {
                out.writeMessagePack(ServerStats.getServerStatsMessagePack(
                    getServerName.get(),
                    getPort.get(),
                    identity,
                    fileManager));

                sender.sendTransaction(new Transaction(transaction,
                                                       byteOutputStream.toByteArray(),
                                                       DatagramConstants.NO_ERROR));
            }
        } catch (NotInitializedException ex) {
            log.warning("File manager not initialized, sending minimal stats");
            // Send minimal stats response
            try {
                ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
                try (var out = new MysterDataOutputStream(byteOutputStream)) {
                    MessagePak minimalStats = MessagePak.newEmpty();
                    minimalStats.putInt(ServerStats.PORT, getPort.get());
                    out.writeMessagePack(minimalStats);
                }
                sender.sendTransaction(new Transaction(transaction,
                                                       byteOutputStream.toByteArray(),
                                                       DatagramConstants.NO_ERROR));
            } catch (IOException e2) {
                throw new BadPacketException("Failed to send minimal stats: " + e2.getMessage());
            }
        } catch (IOException ex) {
            throw new BadPacketException("Bad packet: " + ex.getMessage());
        }
    }
}


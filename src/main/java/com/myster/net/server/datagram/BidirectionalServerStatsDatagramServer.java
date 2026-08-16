package com.myster.net.server.datagram;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Logger;

import com.myster.cid.ServerCid;
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
import com.myster.tracker.PublicKeyIdentity;
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
 *   <li>Suggest the address/identity pair for expected-key verification by the pool</li>
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
        MessagePak clientStats = readClientStats(transaction);
        int advertisedPort = clientStats.getInt(ServerStats.PORT)
                .filter(port -> port >= 1 && port <= 0xFFFF)
                .orElseThrow(() -> new BadPacketException(
                        "Business card requires a port in the range 1..65535"));
        MysterAddress correctedAddress = new MysterAddress(
                transaction.getAddress().getInetAddress(), advertisedPort);
        Optional<PublicKeyIdentity> advertisedIdentity = extractIdentity(clientStats);

        if (transaction.callerCid().isPresent() && advertisedIdentity.isPresent()) {
            ServerCid advertisedCid = ServerCid.fromPublicKey(
                    advertisedIdentity.get().getPublicKey());
            if (!transaction.callerCid().get().equals(advertisedCid)) {
                throw new BadPacketException(
                        "Business-card identity does not match authenticated caller");
            }
        }

        if (advertisedIdentity.isPresent()) {
            pool.suggestAddress(correctedAddress, advertisedIdentity.get());
        } else {
            pool.suggestAddress(correctedAddress);
        }
        log.fine("Received bidirectional server stats from " + correctedAddress);

        MessagePak responseStats;
        try {
            responseStats = ServerStats.getServerStatsMessagePack(
                    getServerName.get(), getPort.get(), identity, fileManager);
        } catch (NotInitializedException exception) {
            responseStats = ServerStats.getMinimalServerStatsMessagePack(
                    getServerName.get(), getPort.get(), identity);
        }

        try {
            ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
            try (var out = new MysterDataOutputStream(byteOutputStream)) {
                out.writeMessagePack(responseStats);
            }
            sender.sendTransaction(new Transaction(transaction,
                                                   byteOutputStream.toByteArray(),
                                                   DatagramConstants.NO_ERROR));
        } catch (IOException exception) {
            throw new BadPacketException("Failed to serialize server stats response: "
                    + exception.getMessage());
        }
    }

    private static MessagePak readClientStats(Transaction transaction) throws BadPacketException {
        try (var in = new MysterDataInputStream(
                new ByteArrayInputStream(transaction.getData()))) {
            return in.readMessagePack();
        } catch (IOException exception) {
            throw new BadPacketException("Malformed business card: " + exception.getMessage());
        }
    }

    private static Optional<PublicKeyIdentity> extractIdentity(MessagePak clientStats)
            throws BadPacketException {
        Optional<byte[]> encodedIdentity = clientStats.getByteArray(ServerStats.IDENTITY);
        if (encodedIdentity.isEmpty()) {
            return Optional.empty();
        }

        try {
            PublicKey publicKey = KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(encodedIdentity.get()));
            return Optional.of(new PublicKeyIdentity(publicKey));
        } catch (GeneralSecurityException exception) {
            throw new BadPacketException("Business card contains an invalid identity");
        }
    }
}

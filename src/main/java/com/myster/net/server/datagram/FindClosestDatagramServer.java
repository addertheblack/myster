package com.myster.net.server.datagram;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.myster.identity.Cid128;
import com.myster.mml.MessagePak;
import com.myster.net.MysterAddress;
import com.myster.net.datagram.BadPacketException;
import com.myster.net.datagram.DatagramConstants;
import com.myster.threedns.ThreeDnsAddressCandidateSet;
import com.myster.tracker.IdentityNeighborSet;
import com.myster.tracker.MysterServer;
import com.myster.tracker.MysterServerPool;
import com.myster.tracker.PublicKeyIdentity;
import com.myster.transaction.Transaction;
import com.myster.transaction.TransactionProtocol;
import com.myster.transaction.TransactionSender;

/** Serves one-hop 3DNS closest-node queries from the local live server pool. */
public class FindClosestDatagramServer implements TransactionProtocol {
    private static final String SCHEMA_VERSION = "/schemaVersion";
    private static final String TARGET_CID = "/targetCid";
    private static final String PER_SIDE_LIMIT = "/perSideLimit";

    private final MysterServerPool pool;

    public FindClosestDatagramServer(MysterServerPool pool) {
        this.pool = java.util.Objects.requireNonNull(pool, "pool");
    }

    @Override
    public int getTransactionCode() {
        return DatagramConstants.THREE_DNS_FIND_CLOSEST_TRANSACTION_CODE;
    }

    @Override
    public void transactionReceived(TransactionSender sender,
                                    Transaction transaction,
                                    Object transactionObject)
            throws BadPacketException {
        try {
            MessagePak request = MessagePak.fromBytes(transaction.getData());
            requireSchemaVersion(request);
            byte[] targetBytes = request.getByteArray(TARGET_CID)
                    .orElseThrow(() -> new BadPacketException("Missing 3DNS target CID"));
            if (targetBytes.length != Cid128.LENGTH) {
                throw new BadPacketException("3DNS target CID must be 16 bytes");
            }

            int requestedLimit = request.getInt(PER_SIDE_LIMIT)
                    .orElse(ThreeDnsAddressCandidateSet.DEFAULT_PER_SIDE_LIMIT);
            int limit = ThreeDnsAddressCandidateSet.normalizePerSideLimit(requestedLimit);
            IdentityNeighborSet neighbors = pool.findClosestByCid(new Cid128(targetBytes), limit);

            MessagePak response = MessagePak.newEmpty();
            response.putInt(SCHEMA_VERSION, ThreeDnsAddressCandidateSet.SCHEMA_VERSION);

            Optional<WireCandidate> exact = neighbors.exact().flatMap(this::toWireCandidate);
            response.putInt("/exactCount", exact.isPresent() ? 1 : 0);
            exact.ifPresent(candidate -> writeCandidate(response, "/exact", candidate));

            List<WireCandidate> left = toWireCandidates(neighbors.left(), limit);
            List<WireCandidate> right = toWireCandidates(neighbors.right(), limit);
            writeCandidates(response, "/left", left);
            writeCandidates(response, "/right", right);

            byte[] encoded = response.toBytes();
            if (encoded.length > ThreeDnsAddressCandidateSet.MAX_RESPONSE_BYTES) {
                throw new BadPacketException("3DNS FIND_CLOSEST response exceeds byte limit");
            }
            sender.sendTransaction(new Transaction(transaction, encoded, DatagramConstants.NO_ERROR));
        } catch (BadPacketException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new BadPacketException("Bad 3DNS FIND_CLOSEST packet: " + ex.getMessage());
        }
    }

    private static void requireSchemaVersion(MessagePak request) throws BadPacketException {
        int schemaVersion = request.getInt(SCHEMA_VERSION)
                .orElseThrow(() -> new BadPacketException("Missing 3DNS schema version"));
        if (schemaVersion != ThreeDnsAddressCandidateSet.SCHEMA_VERSION) {
            throw new BadPacketException("Unsupported 3DNS schema version: " + schemaVersion);
        }
    }

    private List<WireCandidate> toWireCandidates(List<PublicKeyIdentity> identities, int limit) {
        List<WireCandidate> candidates = new ArrayList<>(limit);
        for (PublicKeyIdentity identity : identities) {
            if (candidates.size() >= limit) {
                break;
            }
            toWireCandidate(identity).ifPresent(candidates::add);
        }
        return List.copyOf(candidates);
    }

    private Optional<WireCandidate> toWireCandidate(PublicKeyIdentity identity) {
        return pool.getCachedMysterServer(identity)
                .filter(MysterServer::getStatus)
                .flatMap(server -> {
                    List<MysterAddress> upAddresses = Arrays.asList(server.getUpAddresses());
                    if (upAddresses.isEmpty()) {
                        return Optional.empty();
                    }
                    MysterAddress address = server.getBestAddress()
                            .filter(upAddresses::contains)
                            .orElse(upAddresses.getFirst());
                    if (address.getPort() <= 0 || address.getPort() > 0xFFFF) {
                        return Optional.empty();
                    }
                    return Optional.of(new WireCandidate(identity, address));
                });
    }

    private static void writeCandidates(MessagePak response,
                                        String groupPath,
                                        List<WireCandidate> candidates) {
        response.putInt(groupPath + "Count", candidates.size());
        for (int index = 0; index < candidates.size(); index++) {
            writeCandidate(response, groupPath + "/" + index, candidates.get(index));
        }
    }

    private static void writeCandidate(MessagePak response, String path, WireCandidate candidate) {
        response.putByteArray(path + "/publicKey", candidate.identity().getPublicKey().getEncoded());
        response.putString(path + "/ip", candidate.address().getIP());
        response.putInt(path + "/port", candidate.address().getPort());
    }

    private record WireCandidate(PublicKeyIdentity identity, MysterAddress address) {}
}

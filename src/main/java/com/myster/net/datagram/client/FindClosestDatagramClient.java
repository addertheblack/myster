package com.myster.net.datagram.client;

import java.io.IOException;
import java.net.InetAddress;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.myster.cid.ServerCid;
import com.myster.identity.Util;
import com.myster.mml.MessagePak;
import com.myster.net.MysterAddress;
import com.myster.net.datagram.DatagramConstants;
import com.myster.threedns.ThreeDnsAddressCandidate;
import com.myster.threedns.ThreeDnsAddressCandidateSet;
import com.myster.tracker.PublicKeyIdentity;
import com.myster.transaction.Transaction;

/**
 * Serializes one 3DNS {@code FIND_CLOSEST} request and validates its response.
 * An exact-group candidate is accepted only when its locally derived CID equals
 * the request target.
 */
public class FindClosestDatagramClient implements StandardDatagramClientImpl<ThreeDnsAddressCandidateSet> {
    private static final String SCHEMA_VERSION = "/schemaVersion";
    private static final String TARGET_CID = "/targetCid";
    private static final String PER_SIDE_LIMIT = "/perSideLimit";

    private final ServerCid target;
    private final int perSideLimit;

    public FindClosestDatagramClient(ServerCid target, int perSideLimit) {
        this.target = Objects.requireNonNull(target, "target");
        this.perSideLimit = ThreeDnsAddressCandidateSet.normalizePerSideLimit(perSideLimit);
    }

    @Override
    public ThreeDnsAddressCandidateSet getObjectFromTransaction(Transaction reply) throws IOException {
        if (reply.getData().length > ThreeDnsAddressCandidateSet.MAX_RESPONSE_BYTES) {
            throw new IOException("3DNS FIND_CLOSEST response exceeds byte limit");
        }

        MessagePak response = MessagePak.fromBytes(reply.getData());
        requireSchemaVersion(response);

        int exactCount = requireCount(response, "/exactCount", 1);
        Optional<ThreeDnsAddressCandidate> exact = readExactCandidate(response, exactCount);
        int leftCount = requireCount(response,
                                     "/leftCount",
                                     ThreeDnsAddressCandidateSet.MAX_PER_SIDE_LIMIT);
        int rightCount = requireCount(response,
                                      "/rightCount",
                                      ThreeDnsAddressCandidateSet.MAX_PER_SIDE_LIMIT);

        return new ThreeDnsAddressCandidateSet(exact,
                                               readCandidates(response, "/left", leftCount),
                                               readCandidates(response, "/right", rightCount));
    }

    @Override
    public byte[] getDataForOutgoingPacket() {
        MessagePak request = MessagePak.newEmpty();
        request.putInt(SCHEMA_VERSION, ThreeDnsAddressCandidateSet.SCHEMA_VERSION);
        request.putByteArray(TARGET_CID, target.bytes());
        request.putInt(PER_SIDE_LIMIT, perSideLimit);
        try {
            return request.toBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not serialize 3DNS FIND_CLOSEST request", exception);
        }
    }

    @Override
    public int getCode() {
        return DatagramConstants.THREE_DNS_FIND_CLOSEST_TRANSACTION_CODE;
    }

    private static void requireSchemaVersion(MessagePak response) throws IOException {
        int schemaVersion = response.getInt(SCHEMA_VERSION)
                .orElseThrow(() -> new IOException("Missing 3DNS schema version"));
        if (schemaVersion != ThreeDnsAddressCandidateSet.SCHEMA_VERSION) {
            throw new IOException("Unsupported 3DNS schema version: " + schemaVersion);
        }
    }

    private static int requireCount(MessagePak response, String path, int maximum) throws IOException {
        int count = response.getInt(path)
                .orElseThrow(() -> new IOException("Missing 3DNS candidate count: " + path));
        if (count < 0 || count > maximum) {
            throw new IOException("Invalid 3DNS candidate count at " + path + ": " + count);
        }
        return count;
    }

    private Optional<ThreeDnsAddressCandidate> readExactCandidate(MessagePak response,
                                                                  int exactCount)
            throws IOException {
        if (exactCount == 0) {
            return Optional.empty();
        }

        ThreeDnsAddressCandidate candidate = readCandidate(response, "/exact");
        if (!candidate.cid().equals(target)) {
            throw new IOException("3DNS exact candidate CID does not match request target");
        }
        return Optional.of(candidate);
    }

    private static List<ThreeDnsAddressCandidate> readCandidates(MessagePak response,
                                                                 String groupPath,
                                                                 int count)
            throws IOException {
        List<ThreeDnsAddressCandidate> candidates = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            candidates.add(readCandidate(response, groupPath + "/" + index));
        }
        return List.copyOf(candidates);
    }

    private static ThreeDnsAddressCandidate readCandidate(MessagePak response, String path)
            throws IOException {
        byte[] encodedKey = response.getByteArray(path + "/publicKey")
                .orElseThrow(() -> new IOException("Missing candidate public key at " + path));
        PublicKey publicKey = Util.publicKeyFromBytes(encodedKey)
                .orElseThrow(() -> new IOException("Invalid candidate public key at " + path));
        String ip = response.getString(path + "/ip")
                .orElseThrow(() -> new IOException("Missing candidate IP at " + path));
        if ((!ip.contains(".") && !ip.contains(":")) || !ip.matches("[0-9a-fA-F:.]+")) {
            throw new IOException("Candidate address is not a literal IP at " + path);
        }
        int port = response.getInt(path + "/port")
                .orElseThrow(() -> new IOException("Missing candidate port at " + path));
        if (port <= 0 || port > 0xFFFF) {
            throw new IOException("Invalid candidate port at " + path + ": " + port);
        }

        InetAddress inetAddress = InetAddress.getByName(ip);
        return new ThreeDnsAddressCandidate(new PublicKeyIdentity(publicKey),
                                            new MysterAddress(inetAddress, port));
    }
}

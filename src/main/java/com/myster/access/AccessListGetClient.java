package com.myster.access;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import com.myster.net.MysterSocket;
import com.myster.net.stream.client.MysterDataInputStream;
import com.myster.net.stream.client.MysterDataOutputStream;
import com.myster.type.MysterType;

/**
 * Client for fetching access lists from remote servers via the ACCESS_LIST_GET
 * protocol (section 125).
 *
 * <p>Protocol matches {@link AccessListGetServer}: sends 16-byte MysterType and 32-byte
 * known_tip_hash, receives status + total_bytes_remaining + size-prefixed block stream.
 *
 * <p>All methods are plain blocking calls on the calling thread. Callers that need async
 * behaviour should wrap with {@link com.general.thread.PromiseFutures#execute}.
 */
public class AccessListGetClient {
    private static final Logger log = Logger.getLogger(AccessListGetClient.class.getName());

    private static final int SECTION_NUMBER = 125;

    private static final int STATUS_OK = 0;
    private static final int STATUS_NOT_FOUND = 1;
    private static final int STATUS_FORK_DETECTED = 2;
    private static final int STATUS_ERROR = 3;

    /** Maximum total payload size before the client rejects the transfer (10 MB). */
    private static final long MAX_TOTAL_BYTES = 10 * 1024 * 1024;

    /**
     * Fetches a complete chain over a caller-owned socket. The socket remains open so a pinned TLS
     * connection can be shared with invitation redemption.
     */
    public static Optional<AccessList> fetchAccessList(MysterSocket socket, MysterType mysterType)
            throws IOException {
        return fetchAccessList(socket, mysterType, new byte[32]);
    }

    private static Optional<AccessList> fetchAccessList(MysterSocket socket, MysterType mysterType,
            byte[] knownTipHash) throws IOException {
        MysterDataOutputStream out = socket.out;
        MysterDataInputStream in = socket.in;

        out.writeInt(SECTION_NUMBER);
        out.flush();
        int response = in.read();
        if (response != 1) {
            throw new IOException("Server rejected protocol section: " + response);
        }

        out.write(mysterType.toBytes());
        out.write(knownTipHash);
        out.flush();

        return switch (in.readInt()) {
            case STATUS_OK -> readAccessList(mysterType, in);
            case STATUS_NOT_FOUND -> throw new IOException("Access list not found on server");
            case STATUS_FORK_DETECTED ->
                    throw new IOException("Fork detected: known_tip_hash not in server's chain");
            case STATUS_ERROR -> throw new IOException("Server error processing request");
            default -> throw new IOException("Unknown access-list status code");
        };
    }

    private static Optional<AccessList> readAccessList(MysterType mysterType,
                                                       MysterDataInputStream in) throws IOException {
        long totalBytesRemaining = in.readLong();

        if (totalBytesRemaining < Integer.BYTES || totalBytesRemaining > MAX_TOTAL_BYTES) {
            throw new IOException("Invalid access list payload size: "
                    + totalBytesRemaining + " bytes");
        }

        List<AccessBlock> blocks = new ArrayList<>();
        long remaining = totalBytesRemaining;
        while (true) {
            if (remaining < Integer.BYTES) {
                throw new IOException("Access list block stream exceeds declared size");
            }
            int blockSize = in.readInt();
            remaining -= Integer.BYTES;
            if (blockSize == 0) {
                if (remaining != 0) {
                    throw new IOException("Access list block stream is shorter than declared");
                }
                break;
            }
            if (blockSize < 0 || blockSize > remaining) {
                throw new IOException("Invalid access list block size");
            }

            byte[] blockData = new byte[blockSize];
            in.readFully(blockData);
            remaining -= blockSize;
            blocks.add(AccessListStorageUtils.readBlock(new ByteArrayInputStream(blockData)));
        }

        if (blocks.isEmpty()) {
            log.info("Already up-to-date for type: " + mysterType.toHexString());
            return Optional.empty();
        }

        AccessList accessList = AccessList.fromBlocks(blocks, mysterType);

        if (!accessList.getMysterType().equals(mysterType)) {
            throw new IOException("MysterType mismatch in response");
        }

        log.info("Fetched " + blocks.size() + " blocks");
        return Optional.of(accessList);
    }
}

package com.myster.access;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import com.general.thread.PromiseFuture;
import com.general.thread.PromiseFutures;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.net.stream.client.MysterDataInputStream;
import com.myster.net.stream.client.MysterDataOutputStream;
import com.myster.net.stream.client.MysterSocketFactory;
import com.myster.type.MysterType;

/**
 * Client for fetching access lists from remote servers via the ACCESS_LIST_GET
 * protocol (section 125).
 *
 * <p>Protocol matches {@link AccessListGetServer}: sends 16-byte MysterType and 32-byte
 * known_tip_hash, receives status + total_bytes_remaining + size-prefixed block stream.
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
     * Fetches a complete access list from a server (full chain from genesis).
     *
     * @param server the server address
     * @param mysterType the type to fetch
     * @return future containing the AccessList, or empty if the server has no blocks
     */
    public static PromiseFuture<Optional<AccessList>> fetchAccessList(MysterAddress server,
                                                                      MysterType mysterType) {
        return fetchAccessList(server, mysterType, new byte[32]);
    }

    /**
     * Fetches an access list, sending the known_tip_hash for incremental updates.
     *
     * <p>Returns {@code Optional.empty()} if the client is already up-to-date (the server
     * responded OK but sent zero blocks because known_tip_hash matched the tip).
     *
     * @param server the server address
     * @param mysterType the type to fetch
     * @param knownTipHash hash of the client's latest block (all zeros for full fetch)
     * @return future containing the AccessList if new blocks were received, or empty if up-to-date
     */
    public static PromiseFuture<Optional<AccessList>> fetchAccessList(MysterAddress server,
                                                                      MysterType mysterType,
                                                                      byte[] knownTipHash) {
        return PromiseFutures.execute(() -> {
            log.info("Fetching access list from " + server + " for type: " + mysterType.toHexString());

            try (MysterSocket socket = MysterSocketFactory.makeStreamConnection(server)) {
                MysterDataOutputStream out = socket.out;
                MysterDataInputStream in = socket.in;

                out.writeInt(SECTION_NUMBER);

                int response = in.read();
                if (response != 1) {
                    throw new IOException("Server rejected protocol section: " + response);
                }

                // Send request: 16-byte MysterType + 32-byte known_tip_hash
                out.write(mysterType.toBytes());
                out.write(knownTipHash);
                out.flush();

                int status = in.readInt();

                switch (status) {
                    case STATUS_OK -> {
                        return readAccessList(mysterType, in);
                    }
                    case STATUS_NOT_FOUND -> throw new IOException("Access list not found on server");
                    case STATUS_FORK_DETECTED -> throw new IOException("Fork detected: known_tip_hash not in server's chain");
                    case STATUS_ERROR -> throw new IOException("Server error processing request");
                    default -> throw new IOException("Unknown status code: " + status);
                }

            } catch (IOException e) {
                log.severe("Failed to fetch access list: " + e.getMessage());
                throw e;
            }
        });
    }

    private static Optional<AccessList> readAccessList(MysterType mysterType,
                                                       MysterDataInputStream in) throws IOException {
        long totalBytesRemaining = in.readLong();

        if (totalBytesRemaining > MAX_TOTAL_BYTES) {
            throw new IOException("Access list too large: " + totalBytesRemaining + " bytes");
        }

        List<AccessBlock> blocks = new ArrayList<>();
        while (true) {
            int blockSize = in.readInt();
            if (blockSize == 0) {
                break;
            }

            byte[] blockData = new byte[blockSize];
            in.readFully(blockData);
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

    /**
     * Tries to fetch an access list from multiple onramp servers in order.
     *
     * @param mysterType the type to fetch
     * @param onramps list of server addresses ("host:port" or "host")
     * @return future containing the AccessList from the first successful server, or empty
     */
    public static PromiseFuture<Optional<AccessList>> fetchFromOnramps(MysterType mysterType,
                                                                       List<String> onramps) {
        return PromiseFutures.execute(() -> {
            IOException lastException = null;

            for (String onramp : onramps) {
                try {
                    MysterAddress address = MysterAddress.createMysterAddress(onramp);
                    return fetchAccessList(address, mysterType).get();
                } catch (Exception e) {
                    lastException = new IOException("Failed to fetch from " + onramp, e);
                    log.warning("Failed to fetch from onramp " + onramp + ": " + e.getMessage());
                }
            }

            if (lastException != null) {
                throw lastException;
            }
            throw new IOException("No onramps provided");
        });
    }
}


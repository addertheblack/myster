package com.myster.access;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import com.myster.net.server.ConnectionContext;
import com.myster.net.stream.server.ServerStreamHandler;
import com.myster.type.MysterType;

/**
 * TCP server handler for the ACCESS_LIST_GET protocol (section 125).
 *
 * <p>Protocol:
 * <pre>
 * Request:
 *   [16 bytes] myster_type (MysterType shortBytes)
 *   [32 bytes] known_tip_hash (all zeros = full chain request)
 *
 * Response:
 *   [4 bytes] status_code
 *     0 = OK
 *     1 = NOT_FOUND
 *     2 = FORK_DETECTED (known_tip_hash not in server's chain)
 *     3 = ERROR
 *
 *   If status == OK:
 *     [8 bytes] total_bytes_remaining
 *     [blocks streamed, each prefixed with its byte size]:
 *       [4 bytes] block_byte_size (0 = end of stream)
 *       [block_byte_size bytes] block data
 *       ...repeat...
 *       [4 bytes] 0 (sentinel)
 * </pre>
 */
public class AccessListGetServer extends ServerStreamHandler {
    public static final int NUMBER = 125;

    private static final Logger log = Logger.getLogger(AccessListGetServer.class.getName());

    private static final int STATUS_OK = 0;
    private static final int STATUS_NOT_FOUND = 1;
    private static final int STATUS_FORK_DETECTED = 2;
    private static final int STATUS_ERROR = 3;

    private final AccessListManager accessListManager;

    public AccessListGetServer(AccessListManager accessListManager) {
        this.accessListManager = accessListManager;
    }

    @Override
    public int getSectionNumber() {
        return NUMBER;
    }

    @Override
    public void section(ConnectionContext context) throws IOException {
        try {
            // Read 16-byte MysterType shortBytes
            byte[] mysterTypeBytes = new byte[16];
            context.socket().in.readFully(mysterTypeBytes);
            MysterType mysterType = new MysterType(mysterTypeBytes);

            // Read 32-byte known_tip_hash
            byte[] knownTipHash = new byte[32];
            context.socket().in.readFully(knownTipHash);

            boolean isFullRequest = isAllZeros(knownTipHash);

            log.fine("ACCESS_LIST_GET for type: " + mysterType.toHexString() +
                    " fullRequest=" + isFullRequest);

            Optional<AccessList> optAccessList = accessListManager.loadAccessList(mysterType);

            if (optAccessList.isEmpty()) {
                context.socket().out.writeInt(STATUS_NOT_FOUND);
                context.socket().out.flush();
                return;
            }

            AccessList accessList = optAccessList.get();
            List<AccessBlock> allBlocks = accessList.getBlocks();

            // Determine which blocks to send
            int startIndex;
            if (isFullRequest) {
                startIndex = 0;
            } else {
                startIndex = findBlockAfterHash(allBlocks, knownTipHash);
                if (startIndex == -1) {
                    context.socket().out.writeInt(STATUS_FORK_DETECTED);
                    context.socket().out.flush();
                    return;
                }
            }

            List<AccessBlock> blocksToSend = allBlocks.subList(startIndex, allBlocks.size());

            // Pre-serialize blocks to compute total_bytes_remaining
            byte[][] serializedBlocks = new byte[blocksToSend.size()][];
            for (int i = 0; i < blocksToSend.size(); i++) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                AccessListStorageUtils.writeBlock(blocksToSend.get(i), baos);
                serializedBlocks[i] = baos.toByteArray();
            }

            // total_bytes_remaining = sum of (4 + blockSize) for each block + 4 for sentinel
            long totalBytes = 4; // sentinel
            for (byte[] blockBytes : serializedBlocks) {
                totalBytes += 4 + blockBytes.length;
            }

            // Write response
            context.socket().out.writeInt(STATUS_OK);
            context.socket().out.writeLong(totalBytes);

            for (byte[] blockBytes : serializedBlocks) {
                context.socket().out.writeInt(blockBytes.length);
                context.socket().out.write(blockBytes);
            }

            // Sentinel
            context.socket().out.writeInt(0);
            context.socket().out.flush();

            log.info("Sent " + blocksToSend.size() + " blocks for type: " + mysterType.toHexString());

        } catch (IOException e) {
            log.severe("Error handling ACCESS_LIST_GET: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Finds the index of the first block AFTER the block with the given hash.
     * Returns 0-based index to start sending from, or -1 if hash not found (fork).
     * If the hash matches the tip, returns allBlocks.size() (nothing to send).
     */
    private static int findBlockAfterHash(List<AccessBlock> allBlocks, byte[] hash) {
        for (int i = 0; i < allBlocks.size(); i++) {
            if (Arrays.equals(allBlocks.get(i).computeHash(), hash)) {
                return i + 1;
            }
        }
        return -1;
    }

    private static boolean isAllZeros(byte[] bytes) {
        for (byte b : bytes) {
            if (b != 0) return false;
        }
        return true;
    }
}

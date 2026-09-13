package com.myster.net.stream.client.msdownload;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import com.myster.cid.ServerCid;
import com.myster.hash.FileHash;
import com.myster.mml.MessagePak;
import com.myster.type.MysterType;

/**
 * Append-only supplying-server CIDs beside a partial download. Disk operations run on the calling
 * thread under this object's monitor, like the partial-file layer; reads during recovery run on
 * recovery workers. Source-list failure never changes the partial file or its bitmap.
 *
 * <p>The file starts with MSSS, a big-endian header length and a versioned MessagePak ownership
 * header. Subsequent length-framed MessagePak records contain only /cid (16 bytes). Frames are
 * limited to 64 KiB, but the number of identities is unlimited. Only an incomplete final frame
 * may be truncated on recovery; complete identities are never expired or evicted.
 */
final class DownloadSourceStore {
    private static final Logger LOG = Logger.getLogger(DownloadSourceStore.class.getName());

    static final int MAGIC = 0x4d535353;
    static final int MAX_FRAME = 64 * 1024;

    private final Path path;
    private final MysterType type;
    private final long length;
    private final FileHash[] hashes;
    private final Set<ServerCid> durable = new LinkedHashSet<>();
    private final Set<ServerCid> pending = new LinkedHashSet<>();
    private boolean closed;
    private boolean loaded;
    private boolean writable = true;
    private long validEnd;

    DownloadSourceStore(Path partialPath, MysterType type, long length, FileHash[] hashes,
                        boolean reset) {
        String name = partialPath.getFileName().toString();
        path = partialPath.resolveSibling(name.substring(0, name.length() - 2) + ".s");
        this.type = type;
        this.length = length;
        this.hashes = hashes.clone();

        if (reset) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                writable = false;
                warn(e);
            }
        }
    }

    /** Reads the complete usable prefix, retaining failed and disconnected identities. May block. */
    synchronized List<ServerCid> read() {
        loadSafely();
        return List.copyOf(durable);
    }

    /** Appends a supplying identity; repeated notifications also retry failed writes. May block. */
    synchronized void record(ServerCid cid) {
        if (closed) {
            return;
        }
        pending.add(cid);
        flush();
    }

    /** Stops accepting sources and flushes pending writes before returning. May block. */
    synchronized void close() {
        closed = true;
        flush();
    }

    /** Deletes the source list under the write lock; no late append can recreate it. May block. */
    synchronized void delete() {
        closed = true;
        pending.clear();
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            warn(e);
        }
    }

    private void loadSafely() {
        if (loaded || !writable) {
            return;
        }
        try {
            load();
            loaded = true;
        } catch (IOException e) {
            warn(e);
        }
    }

    private void load() throws IOException {
        durable.clear();
        validEnd = 0;
        if (!Files.exists(path)) {
            return;
        }
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            if (file.length() < 8 || file.readInt() != MAGIC) {
                disable();
                return;
            }
            int size = file.readInt();
            if (!validSize(size) || file.length() - file.getFilePointer() < size) {
                disable();
                return;
            }
            MessagePak header;
            try {
                header = MessagePak.fromBytes(readBytes(file, size), MAX_FRAME);
            } catch (IOException e) {
                disable();
                return;
            }
            if (!owns(header)) {
                disable();
                return;
            }
            validEnd = file.getFilePointer();
            while (file.getFilePointer() < file.length()) {
                if (file.length() - file.getFilePointer() < 4) {
                    break;
                }
                size = file.readInt();
                if (!validSize(size)) {
                    disable();
                    return;
                }
                if (file.length() - file.getFilePointer() < size) {
                    break;
                }
                byte[] bytes = readBytes(file, size);
                validEnd = file.getFilePointer();
                try {
                    byte[] cid = MessagePak.fromBytes(bytes, MAX_FRAME).getByteArray("/cid")
                            .orElse(new byte[0]);
                    if (cid.length == ServerCid.LENGTH) {
                        durable.add(new ServerCid(cid));
                    }
                } catch (IOException e) {
                    LOG.fine("Ignoring malformed framed download source in " + path);
                }
            }
        }
    }

    private void disable() {
        writable = false;
        LOG.warning("Unusable download server-list file; leaving it untouched: " + path);
    }

    private boolean owns(MessagePak header) {
        if (header.getInt("/version").orElse(-1) != 1
                || header.getLong("/length").orElse(-1L) != length
                || !Arrays.equals(header.getByteArray("/type").orElse(null), type.toBytes())
                || header.getInt("/hashCount").orElse(-1) != hashes.length) {
            return false;
        }
        for (int i = 0; i < hashes.length; i++) {
            if (!header.getString("/hashes/" + i + "/name").orElse("")
                    .equals(hashes[i].getHashName())
                    || !Arrays.equals(header.getByteArray("/hashes/" + i + "/value").orElse(null),
                                      hashes[i].getBytes())) {
                return false;
            }
        }
        return true;
    }

    private byte[] headerBytes() throws IOException {
        MessagePak header = MessagePak.newEmpty();
        header.putInt("/version", 1);
        header.putLong("/length", length);
        header.putByteArray("/type", type.toBytes());
        header.putInt("/hashCount", hashes.length);
        for (int i = 0; i < hashes.length; i++) {
            header.putString("/hashes/" + i + "/name", hashes[i].getHashName());
            header.putByteArray("/hashes/" + i + "/value", hashes[i].getBytes());
        }
        byte[] bytes = header.toBytes();
        if (!validSize(bytes.length)) {
            throw new IOException("Download source header is too large");
        }
        return bytes;
    }

    private void create() throws IOException {
        Path temporary = Files.createTempFile(path.toAbsolutePath().getParent(), "ms-", ".tmp");
        try {
            try (RandomAccessFile file = new RandomAccessFile(temporary.toFile(), "rw")) {
                byte[] bytes = headerBytes();
                file.writeInt(MAGIC);
                file.writeInt(bytes.length);
                file.write(bytes);
                file.getFD().sync();
            }
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE);
            validEnd = Files.size(path);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void flush() {
        loadSafely();
        if (!loaded || !writable) {
            return;
        }
        pending.removeAll(durable);
        if (pending.isEmpty()) {
            return;
        }
        try {
            if (validEnd == 0) {
                create();
            }
            try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "rw")) {
                file.setLength(validEnd);
                file.seek(validEnd);
                for (ServerCid cid : pending) {
                    MessagePak record = MessagePak.newEmpty();
                    record.putByteArray("/cid", cid.bytes());
                    byte[] bytes = record.toBytes();
                    file.writeInt(bytes.length);
                    file.write(bytes);
                }
                file.getFD().sync();
                validEnd = file.getFilePointer();
                durable.addAll(pending);
                pending.clear();
            }
        } catch (IOException e) {
            // Re-read complete frames after a partial write; never discard a complete identity.
            loaded = false;
            warn(e);
        }
    }

    private static boolean validSize(int size) {
        return size > 0 && size <= MAX_FRAME;
    }

    private static byte[] readBytes(RandomAccessFile file, int size) throws IOException {
        byte[] bytes = new byte[size];
        file.readFully(bytes);
        return bytes;
    }

    private void warn(IOException e) {
        LOG.warning("Download server-list I/O failed for " + path + ": " + e.getMessage());
    }
}

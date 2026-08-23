package com.myster.filemanager;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.CRC32C;

import com.myster.mml.MessagePak;

/**
 * Reads and writes one append-only metadata cache shard.
 *
 * <h2>Storage format version 2</h2>
 *
 * A shard file is one fixed-size header followed by zero or more independently framed records:
 *
 * <pre>
 * File header (13 bytes)
 * Offset  Size  Encoding  Meaning
 *      0     8  byte[8]   ASCII "MYSTMCAC"
 *      8     4  int32     Storage format version (currently 2)
 *     12     1  uint8     Shard id (0 through 255)
 *
 * Record frame (24 + N bytes, offsets relative to the start of the frame)
 * Offset  Size  Encoding  Meaning
 *      0     4  int32     Record magic 0x4D435243 (ASCII "MCRC")
 *      4     4  int32     MessagePak body length N
 *      8     8  int64     Sequence number
 *     16     N  byte[N]   One complete MessagePak body
 *   16+N     4  uint32    CRC32C
 *   20+N     4  int32     End marker 0x4D43454E (ASCII "MCEN")
 * </pre>
 *
 * All multi-byte envelope values use big-endian byte order. Body length must be from 1 through
 * 16 MiB, inclusive. Sequence numbers start at 1, increase by exactly one, and restart at 1 when
 * compaction replaces the file. The CRC32C input is the concatenation of the big-endian body
 * length, big-endian sequence number, and body bytes; it does not include either magic value.
 *
 * <h2>MessagePak body</h2>
 *
 * The framing codec treats the body as opaque. {@link ShardedFileMetadataCache} currently writes
 * one operation map per frame. Every operation contains:
 *
 * <pre>
 * /operation       string  "put" or "remove"
 * /entryKey        string  64-character lowercase hexadecimal entry hash
 * /metadataTypeId  string  Non-blank stable metadata type id
 * /cacheVersion    int32   Positive metadata-type cache version
 * </pre>
 *
 * A {@code put} additionally contains:
 *
 * <pre>
 * /path                string  Non-blank normalized absolute path
 * /size                int64   Non-negative file size in bytes
 * /lastModifiedMillis  int64   File modification time in epoch milliseconds
 * /createdAtMillis     int64   Cache creation time in epoch milliseconds
 * /metadata/           tree    Optional extracted metadata; empty means a negative cache entry
 * </pre>
 *
 * A {@code remove} is a tombstone. It removes the entry identified by {@code /entryKey}, regardless
 * of which metadata type id or cache version is currently stored for that entry. The file-header
 * storage version describes this binary format; the cache version in each operation describes the
 * metadata fields and extraction semantics for one metadata type.
 *
 * <h2>Replay and recovery</h2>
 *
 * A frame is applied only after its complete envelope, checksum, and MessagePak body validate. An
 * incomplete final frame produces {@link ReplayStatus#TORN_TAIL}; callers may retain the validated
 * prefix and truncate to {@link ReplayResult#validLength()}. A bad or incomplete header, wrong
 * shard id, non-contiguous sequence, unreasonable length, invalid complete frame, or malformed body
 * produces {@link ReplayStatus#INVALID}. Replay does not attempt to recover records after an invalid
 * frame. Filesystem and channel failures are reported separately as {@link IOException} so callers
 * do not mistake an operational failure for disposable corrupt cache data.
 * <p>
 * Callers must serialize all operations on one log.
 */
final class MetadataCacheLog {
    private static final int STORAGE_FORMAT_VERSION = 2;
    static final int MAX_RECORD_BYTES = 16 * 1024 * 1024;

    // Myster Metadata CAChe file format:
    private static final byte[] FILE_MAGIC = {'M', 'Y', 'S', 'T', 'M', 'C', 'A', 'C'};
    private static final int RECORD_MAGIC = 0x4d435243;
    private static final int END_MARKER = 0x4d43454e;
    private static final int FILE_HEADER_BYTES = FILE_MAGIC.length + Integer.BYTES + Byte.BYTES;
    private static final int RECORD_PREFIX_BYTES = Integer.BYTES + Integer.BYTES + Long.BYTES;
    private static final int RECORD_SUFFIX_BYTES = Integer.BYTES + Integer.BYTES;

    private final Path path;
    private final int shardId;

    MetadataCacheLog(Path path, int shardId) {
        this.path = Objects.requireNonNull(path);
        if (shardId < 0 || shardId > 255) {
            throw new IllegalArgumentException("shardId must be between 0 and 255");
        }
        this.shardId = shardId;
    }

    /**
     * Replays all complete, valid records in this log.
     *
     * @return replayed records and the byte offset after the last valid frame
     *          (be sure to check for invalid file conditions!)
     * @throws IOException if the file cannot be read
     */
    ReplayResult replay() throws IOException {
        if (!Files.exists(path)) {
            return ReplayResult.valid(List.of(), 0, 1);
        }

        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            long fileSize = channel.size();
            Optional<String> headerError = validateFileHeader(channel, fileSize);
            if (headerError.isPresent()) {
                return ReplayResult.invalid(headerError.get());
            }
            return replayFrames(channel, fileSize);
        }
    }

    private Optional<String> validateFileHeader(FileChannel channel, long fileSize)
            throws IOException {
        if (fileSize < FILE_HEADER_BYTES) {
            return Optional.of("incomplete file header");
        }

        ByteBuffer header = ByteBuffer.allocate(FILE_HEADER_BYTES);
        readFully(channel, header, 0);
        header.flip();
        byte[] magic = new byte[FILE_MAGIC.length];
        header.get(magic);
        int version = header.getInt();
        int storedShardId = Byte.toUnsignedInt(header.get());
        if (!Arrays.equals(FILE_MAGIC, magic)) {
            return Optional.of("invalid file magic");
        }
        if (version != STORAGE_FORMAT_VERSION) {
            return Optional.of("unsupported storage format version " + version);
        }
        if (storedShardId != shardId) {
            return Optional.of("file belongs to shard " + storedShardId);
        }
        return Optional.empty();
    }

    private static ReplayResult replayFrames(FileChannel channel, long fileSize)
            throws IOException {
        List<Frame> frames = new ArrayList<>();
        long offset = FILE_HEADER_BYTES;
        long expectedSequence = 1;
        while (offset < fileSize) {
            FrameReadResult read = readFrame(channel, fileSize, offset, expectedSequence);
            if (read.status() == ReplayStatus.TORN_TAIL) {
                return ReplayResult.torn(frames, offset, expectedSequence);
            }
            if (read.status() == ReplayStatus.INVALID) {
                return ReplayResult.invalid(read.detail());
            }

            Frame frame = read.frame().orElseThrow();
            frames.add(frame);
            offset = frame.endOffset();
            expectedSequence++;
        }
        return ReplayResult.valid(frames, offset, expectedSequence);
    }

    private static FrameReadResult readFrame(FileChannel channel,
                                             long fileSize,
                                             long offset,
                                             long expectedSequence) throws IOException {
        if (fileSize - offset < RECORD_PREFIX_BYTES) {
            return FrameReadResult.torn();
        }

        ByteBuffer prefix = ByteBuffer.allocate(RECORD_PREFIX_BYTES);
        readFully(channel, prefix, offset);

        prefix.flip();

        int recordMagic = prefix.getInt();
        int bodyLength = prefix.getInt();
        long sequence = prefix.getLong();

        if (recordMagic != RECORD_MAGIC) {
            return FrameReadResult.invalid("invalid record magic at byte " + offset);
        }

        if (bodyLength <= 0 || bodyLength > MAX_RECORD_BYTES) {
            return FrameReadResult.invalid("invalid record length " + bodyLength
                    + " at byte " + offset);
        }

        if (sequence != expectedSequence) {
            return FrameReadResult.invalid("expected sequence " + expectedSequence
                    + " but found " + sequence);
        }

        long frameBytes = (long) RECORD_PREFIX_BYTES + bodyLength + RECORD_SUFFIX_BYTES;
        if (fileSize - offset < frameBytes) {
            return FrameReadResult.torn();
        }

        byte[] bodyBytes = readBody(channel, offset, bodyLength);
        Optional<String> trailerError = validateTrailer(channel, offset, sequence, bodyBytes);
        if (trailerError.isPresent()) {
            return FrameReadResult.invalid(trailerError.get());
        }

        try {
            MessagePak body = MessagePak.fromBytes(bodyBytes);
            return FrameReadResult.valid(new Frame(sequence, body, offset, offset + frameBytes));
        } catch (IOException ex) {
            return FrameReadResult.invalid("invalid MessagePak body at byte " + offset);
        }
    }

    private static byte[] readBody(FileChannel channel, long offset, int bodyLength)
            throws IOException {
        byte[] bodyBytes = new byte[bodyLength];
        readFully(channel, ByteBuffer.wrap(bodyBytes), offset + RECORD_PREFIX_BYTES);
        return bodyBytes;
    }

    private static Optional<String> validateTrailer(FileChannel channel,
                                                     long offset,
                                                     long sequence,
                                                     byte[] bodyBytes) throws IOException {
        ByteBuffer suffix = ByteBuffer.allocate(RECORD_SUFFIX_BYTES);
        readFully(channel, suffix, offset + RECORD_PREFIX_BYTES + bodyBytes.length);
        suffix.flip();
        int storedCrc = suffix.getInt();
        int endMarker = suffix.getInt();
        if (endMarker != END_MARKER) {
            return Optional.of("invalid record end marker at byte " + offset);
        }
        if (storedCrc != crc(bodyBytes.length, sequence, bodyBytes)) {
            return Optional.of("record checksum mismatch at byte " + offset);
        }
        return Optional.empty();
    }

    /**
     * Appends one frame after removing any bytes beyond the caller's last validated offset.
     *
     * @param validLength byte offset immediately after the last validated frame, or zero for a new
     *        file
     * @param sequence positive sequence number for the new frame
     * @param body semantic record body
     * @return byte offset immediately after the appended frame
     * @throws IOException if serialization or writing fails
     */
    long append(long validLength, long sequence, MessagePak body) throws IOException {
        if (validLength < 0) {
            throw new IllegalArgumentException("validLength must not be negative");
        }
        if (sequence <= 0) {
            throw new IllegalArgumentException("sequence must be positive");
        }

        byte[] frame = frame(sequence, Objects.requireNonNull(body));
        Files.createDirectories(path.getParent());
        try (FileChannel channel = FileChannel.open(path,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE)) {
            if (validLength == 0) {
                channel.truncate(0);
                writeFully(channel, fileHeader(), 0);
                validLength = FILE_HEADER_BYTES;
            } else if (channel.size() < validLength) {
                throw new IOException("Metadata cache log is shorter than its validated length");
            }

            channel.truncate(validLength);
            writeFully(channel, ByteBuffer.wrap(frame), validLength);
            return validLength + frame.length;
        }
    }

    /**
     * Replaces this log with a compact generation containing exactly the supplied record bodies.
     * The replacement is forced before it is moved into place.
     *
     * @return scalar replay state for the replacement generation
     * @throws IOException if serialization, writing, forcing, or replacement fails
     */
    RewriteResult rewrite(List<MessagePak> bodies) throws IOException {
        Objects.requireNonNull(bodies);
        Files.createDirectories(path.getParent());
        Path tempFile = Files.createTempFile(path.getParent(), path.getFileName() + "-", ".tmp");
        boolean moved = false;
        try {
            long offset = FILE_HEADER_BYTES;
            long sequence = 1;
            try (FileChannel channel = FileChannel.open(tempFile,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                writeFully(channel, fileHeader(), 0);
                for (MessagePak body : bodies) {
                    byte[] frame = frame(sequence, body);
                    writeFully(channel, ByteBuffer.wrap(frame), offset);
                    offset += frame.length;
                    sequence++;
                }
                channel.force(true);
            }

            try {
                Files.move(tempFile, path, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
            return new RewriteResult(offset, sequence, bodies.size());
        } finally {
            if (!moved) {
                Files.deleteIfExists(tempFile);
            }
        }
    }

    void truncate(long validLength) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
            channel.truncate(validLength);
        }
    }

    void discard() throws IOException {
        Files.deleteIfExists(path);
    }

    void force() throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
            channel.force(false);
        }
    }

    Path path() {
        return path;
    }

    private ByteBuffer fileHeader() {
        return ByteBuffer.allocate(FILE_HEADER_BYTES)
                .put(FILE_MAGIC)
                .putInt(STORAGE_FORMAT_VERSION)
                .put((byte) shardId)
                .flip();
    }

    private static byte[] frame(long sequence, MessagePak body) throws IOException {
        byte[] bodyBytes = body.toBytes();
        if (bodyBytes.length == 0 || bodyBytes.length > MAX_RECORD_BYTES) {
            throw new IOException("Metadata cache record body length is outside supported range: "
                    + bodyBytes.length);
        }

        return ByteBuffer.allocate(RECORD_PREFIX_BYTES + bodyBytes.length + RECORD_SUFFIX_BYTES)
                .putInt(RECORD_MAGIC)
                .putInt(bodyBytes.length)
                .putLong(sequence)
                .put(bodyBytes)
                .putInt(crc(bodyBytes.length, sequence, bodyBytes))
                .putInt(END_MARKER)
                .array();
    }

    private static int crc(int bodyLength, long sequence, byte[] bodyBytes) {
        CRC32C crc = new CRC32C();
        ByteBuffer framedValues = ByteBuffer.allocate(Integer.BYTES + Long.BYTES)
                .putInt(bodyLength)
                .putLong(sequence);
        crc.update(framedValues.array(), 0, framedValues.capacity());
        crc.update(bodyBytes, 0, bodyBytes.length);
        return (int) crc.getValue();
    }

    private static void readFully(FileChannel channel, ByteBuffer destination, long offset)
            throws IOException {
        while (destination.hasRemaining()) {
            int count = channel.read(destination, offset);
            if (count < 0) {
                throw new IOException("Unexpected end of metadata cache log");
            }
            if (count == 0) {
                throw new IOException("Metadata cache log read made no progress");
            }
            offset += count;
        }
    }

    private static void writeFully(FileChannel channel, ByteBuffer source, long offset)
            throws IOException {
        while (source.hasRemaining()) {
            int count = channel.write(source, offset);
            if (count < 0) {
                throw new IOException("Could not write metadata cache log");
            }
            if (count == 0) {
                throw new IOException("Metadata cache log write made no progress");
            }
            offset += count;
        }
    }

    enum ReplayStatus {
        VALID,
        TORN_TAIL,
        INVALID
    }

    private record FrameReadResult(Optional<Frame> frame,
                                   ReplayStatus status,
                                   String detail) {
        private static FrameReadResult valid(Frame frame) {
            return new FrameReadResult(Optional.of(frame), ReplayStatus.VALID, "");
        }

        private static FrameReadResult torn() {
            return new FrameReadResult(Optional.empty(), ReplayStatus.TORN_TAIL,
                    "incomplete final record");
        }

        private static FrameReadResult invalid(String detail) {
            return new FrameReadResult(Optional.empty(), ReplayStatus.INVALID, detail);
        }
    }

    record Frame(long sequence, MessagePak body, long startOffset, long endOffset) {
    }

    record ReplayResult(List<Frame> frames,
                        long validLength,
                        long nextSequence,
                        ReplayStatus status,
                        String detail) {
        ReplayResult {
            frames = List.copyOf(frames);
        }

        private static ReplayResult valid(List<Frame> frames,
                                          long validLength,
                                          long nextSequence) {
            return new ReplayResult(frames, validLength, nextSequence, ReplayStatus.VALID, "");
        }

        private static ReplayResult torn(List<Frame> frames,
                                         long validLength,
                                         long nextSequence) {
            return new ReplayResult(frames, validLength, nextSequence, ReplayStatus.TORN_TAIL,
                    "incomplete final record");
        }

        private static ReplayResult invalid(String detail) {
            return new ReplayResult(List.of(), 0, 1, ReplayStatus.INVALID, detail);
        }
    }

    record RewriteResult(long validLength, long nextSequence, int recordCount) {
    }
}

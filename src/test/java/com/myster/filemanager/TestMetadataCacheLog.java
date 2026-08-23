package com.myster.filemanager;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import com.myster.mml.MessagePak;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestMetadataCacheLog {
    @TempDir
    Path tempDir;

    @Test
    void appendsAndReplaysRecordsWithoutChangingPrefix() throws IOException {
        Path path = tempDir.resolve("00.mlog");
        MetadataCacheLog log = new MetadataCacheLog(path, 0);

        long validLength = log.append(0, 1, body("first"));
        byte[] firstGeneration = Files.readAllBytes(path);
        validLength = log.append(validLength, 2, body("second"));

        byte[] secondGeneration = Files.readAllBytes(path);
        assertArrayEquals(firstGeneration,
                java.util.Arrays.copyOf(secondGeneration, firstGeneration.length));
        MetadataCacheLog.ReplayResult replay = log.replay();
        assertEquals(MetadataCacheLog.ReplayStatus.VALID, replay.status());
        assertEquals(validLength, replay.validLength());
        assertEquals(3, replay.nextSequence());
        assertEquals(List.of("first", "second"), replay.frames().stream()
                .map(frame -> frame.body().getString("/value").orElseThrow())
                .toList());
    }

    @Test
    void truncatedFinalFramePreservesValidPrefixAndCanBeReplaced() throws IOException {
        Path path = tempDir.resolve("2a.mlog");
        MetadataCacheLog log = new MetadataCacheLog(path, 0x2a);
        long firstLength = log.append(0, 1, body("first"));
        long secondLength = log.append(firstLength, 2, body("incomplete"));
        assertTrue(secondLength > firstLength);

        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
            channel.truncate(firstLength + 5);
        }

        MetadataCacheLog.ReplayResult torn = log.replay();
        assertEquals(MetadataCacheLog.ReplayStatus.TORN_TAIL, torn.status());
        assertEquals(firstLength, torn.validLength());
        assertEquals(2, torn.nextSequence());
        assertEquals(1, torn.frames().size());

        log.append(torn.validLength(), torn.nextSequence(), body("replacement"));

        MetadataCacheLog.ReplayResult repaired = log.replay();
        assertEquals(MetadataCacheLog.ReplayStatus.VALID, repaired.status());
        assertEquals(List.of("first", "replacement"), repaired.frames().stream()
                .map(frame -> frame.body().getString("/value").orElseThrow())
                .toList());
    }

    @Test
    void checksumFailureInvalidatesLog() throws IOException {
        Path path = tempDir.resolve("04.mlog");
        MetadataCacheLog log = new MetadataCacheLog(path, 4);
        log.append(0, 1, body("record"));
        MetadataCacheLog.Frame frame = log.replay().frames().getFirst();

        byte[] bytes = Files.readAllBytes(path);
        int bodyOffset = Math.toIntExact(frame.startOffset())
                + Integer.BYTES + Integer.BYTES + Long.BYTES;
        bytes[bodyOffset] ^= 1;
        Files.write(path, bytes);

        assertEquals(MetadataCacheLog.ReplayStatus.INVALID, log.replay().status());
    }

    @Test
    void oversizedBodyLengthIsRejectedBeforeAllocation() throws IOException {
        Path path = tempDir.resolve("05.mlog");
        MetadataCacheLog log = new MetadataCacheLog(path, 5);
        log.append(0, 1, body("record"));
        long recordStart = log.replay().frames().getFirst().startOffset();

        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
            ByteBuffer invalidLength = ByteBuffer.allocate(Integer.BYTES)
                    .putInt(MetadataCacheLog.MAX_RECORD_BYTES + 1)
                    .flip();
            long offset = recordStart + Integer.BYTES;
            while (invalidLength.hasRemaining()) {
                offset += channel.write(invalidLength, offset);
            }
        }

        assertEquals(MetadataCacheLog.ReplayStatus.INVALID, log.replay().status());
    }

    @Test
    void wrongHeaderAndShardAreInvalid() throws IOException {
        Path badMagicPath = tempDir.resolve("bad.mlog");
        Files.writeString(badMagicPath, "not a metadata cache log");
        assertEquals(MetadataCacheLog.ReplayStatus.INVALID,
                new MetadataCacheLog(badMagicPath, 0).replay().status());

        Path wrongShardPath = tempDir.resolve("wrong-shard.mlog");
        MetadataCacheLog writer = new MetadataCacheLog(wrongShardPath, 1);
        writer.append(0, 1, body("record"));
        assertEquals(MetadataCacheLog.ReplayStatus.INVALID,
                new MetadataCacheLog(wrongShardPath, 2).replay().status());
    }

    @Test
    void rewriteDropsOldRecordsAndRestartsSequence() throws IOException {
        Path path = tempDir.resolve("ff.mlog");
        MetadataCacheLog log = new MetadataCacheLog(path, 255);
        long length = log.append(0, 1, body("old-one"));
        log.append(length, 2, body("old-two"));

        MetadataCacheLog.RewriteResult rewritten = log.rewrite(
                List.of(body("live-one"), body("live-two")));

        MetadataCacheLog.ReplayResult replay = log.replay();
        assertEquals(rewritten.validLength(), replay.validLength());
        assertEquals(3, rewritten.nextSequence());
        assertEquals(2, rewritten.recordCount());
        assertEquals(List.of(1L, 2L), replay.frames().stream()
                .map(MetadataCacheLog.Frame::sequence)
                .toList());
        assertEquals(List.of("live-one", "live-two"), replay.frames().stream()
                .map(frame -> frame.body().getString("/value").orElseThrow())
                .toList());
    }

    private static MessagePak body(String value) {
        MessagePak body = MessagePak.newEmpty();
        body.putString("/value", value);
        return body;
    }
}

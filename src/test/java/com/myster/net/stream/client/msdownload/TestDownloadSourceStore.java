package com.myster.net.stream.client.msdownload;

import static org.junit.jupiter.api.Assertions.*;

import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.myster.cid.ServerCid;
import com.myster.hash.FileHash;
import com.myster.mml.MessagePak;
import com.myster.type.MysterType;

class TestDownloadSourceStore {
    @TempDir Path directory;
    private final MysterType type = new MysterType(new byte[16]);

    private DownloadSourceStore open(boolean reset) {
        return new DownloadSourceStore(directory.resolve("movie.p"), type, 123, new FileHash[0], reset);
    }

    private static ServerCid cid(int n) {
        byte[] bytes = new byte[16];
        bytes[15] = (byte) n;
        return new ServerCid(bytes);
    }

    private static List<ServerCid> read(DownloadSourceStore store) throws Exception {
        return store.read();
    }

    @Test void concurrentDeletionAndLateRecordsCannotRecreateSourceFile() throws Exception {
        DownloadSourceStore store = open(true);
        store.record(cid(1));
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        try (var workers = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var writer = workers.submit(() -> {
                start.await();
                for (int i = 2; i < 20; i++) {
                    store.record(cid(i));
                }
                return null;
            });
            var deletion = workers.submit(() -> {
                start.await();
                store.delete();
                return null;
            });
            start.countDown();
            writer.get(5, java.util.concurrent.TimeUnit.SECONDS);
            deletion.get(5, java.util.concurrent.TimeUnit.SECONDS);
        }
        store.record(cid(99));
        store.close();
        assertFalse(Files.exists(directory.resolve("movie.s")));
    }

    @Test void retainsAllIdentitiesAndAppendsWithoutChangingPreviousBytesOrBitmap() throws Exception {
        byte[] bitmap = {1, 2, 3, 4, (byte) 0x80};
        Files.write(directory.resolve("movie.p"), bitmap);
        DownloadSourceStore store = open(true);
        store.record(cid(1));
        assertEquals(List.of(cid(1)), read(store));
        byte[] prefix = Files.readAllBytes(directory.resolve("movie.s"));
        for (int i = 1; i <= 80; i++) store.record(cid(i));
        store.close();
        byte[] all = Files.readAllBytes(directory.resolve("movie.s"));
        assertArrayEquals(prefix, Arrays.copyOf(all, prefix.length));
        assertArrayEquals(bitmap, Files.readAllBytes(directory.resolve("movie.p")));
        assertEquals(80, read(open(false)).size());
        store.record(cid(99));
        assertEquals(80, read(store).size());
    }

    @Test void repairsTornTailAndPreservesEveryCompleteIdentity() throws Exception {
        DownloadSourceStore store = open(true);
        store.record(cid(1));
        store.close();
        Path path = directory.resolve("movie.s");
        byte[] prefix = Files.readAllBytes(path);
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "rw")) {
            file.seek(file.length());
            file.writeInt(100);
            file.write(new byte[] {1, 2, 3});
        }
        DownloadSourceStore resumed = open(false);
        assertEquals(List.of(cid(1)), read(resumed));
        resumed.record(cid(2));
        resumed.close();
        assertEquals(List.of(cid(1), cid(2)), read(open(false)));
        assertArrayEquals(prefix, Arrays.copyOf(Files.readAllBytes(path), prefix.length));
    }

    @Test void skipsMalformedCompleteFramesAndRejectsInvalidFrameLengths() throws Exception {
        DownloadSourceStore store = open(true);
        store.record(cid(1));
        store.close();
        Path path = directory.resolve("movie.s");
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "rw")) {
            file.seek(file.length());
            file.writeInt(1);
            file.writeByte(0xc1); // Reserved MessagePack value, but the frame boundary is intact.
        }
        DownloadSourceStore resumed = open(false);
        resumed.record(cid(2));
        resumed.close();
        assertEquals(List.of(cid(1), cid(2)), read(open(false)));
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "rw")) {
            file.seek(file.length());
            file.writeInt(Integer.MAX_VALUE);
        }
        byte[] broken = Files.readAllBytes(path);
        DownloadSourceStore disabled = open(false);
        assertEquals(List.of(cid(1), cid(2)), read(disabled));
        disabled.record(cid(3));
        disabled.close();
        assertArrayEquals(broken, Files.readAllBytes(path));
    }

    @Test void unknownVersionIsPreservedAndIgnored() throws Exception {
        Path path = directory.resolve("movie.s");
        MessagePak header = MessagePak.newEmpty();
        header.putInt("/version", 99);
        byte[] bytes = header.toBytes();
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "rw")) {
            file.writeInt(DownloadSourceStore.MAGIC);
            file.writeInt(bytes.length);
            file.write(bytes);
        }
        byte[] before = Files.readAllBytes(path);
        DownloadSourceStore store = open(false);
        assertTrue(read(store).isEmpty());
        store.record(cid(1));
        store.close();
        assertArrayEquals(before, Files.readAllBytes(path));
    }

    @Test void wrongOwnerDoesNotReuseOrOverwriteSources() throws Exception {
        DownloadSourceStore store = open(true);
        store.record(cid(1));
        store.close();
        byte[] before = Files.readAllBytes(directory.resolve("movie.s"));
        DownloadSourceStore other = new DownloadSourceStore(directory.resolve("movie.p"), type,
                999, new FileHash[0], false);
        assertTrue(read(other).isEmpty());
        other.record(cid(2));
        other.close();
        assertArrayEquals(before, Files.readAllBytes(directory.resolve("movie.s")));
    }

    @Test void deleteOrdersAfterPendingWritesAndCloseAndCannotBeResurrected() throws Exception {
        DownloadSourceStore store = open(true);
        for (int i = 0; i < 80; i++) store.record(cid(i));
        store.close();
        store.delete();
        store.record(cid(90));
        store.close();
        assertFalse(Files.exists(directory.resolve("movie.s")));
    }

    @Test void newPartialClearsStaleSourcesAndMissingFileIsFine() throws Exception {
        assertTrue(read(open(false)).isEmpty());
        DownloadSourceStore first = open(true);
        first.record(cid(1));
        first.close();
        DownloadSourceStore replacement = open(true);
        assertTrue(read(replacement).isEmpty());
        replacement.record(cid(2));
        replacement.close();
        assertEquals(List.of(cid(2)), read(open(false)));
    }

    @Test void failedAppendIsRetriedOnLaterActivity() throws Exception {
        Path parent = directory.resolve("blocked");
        Files.writeString(parent, "not a directory");
        DownloadSourceStore store = new DownloadSourceStore(parent.resolve("movie.p"), type, 123,
                new FileHash[0], false);
        store.record(cid(1));
        read(store); // The failed create has completed.
        Files.delete(parent);
        Files.createDirectory(parent);
        store.record(cid(2));
        store.close();
        assertEquals(List.of(cid(1), cid(2)), read(store));
    }

    @Test void longBasenameUsesShortTemporaryFilenameAndStoresOnlyCid() throws Exception {
        String name = "a".repeat(245);
        DownloadSourceStore store = new DownloadSourceStore(directory.resolve(name + ".p"), type,
                123, new FileHash[0], true);
        store.record(cid(1));
        store.close();
        try (RandomAccessFile file = new RandomAccessFile(directory.resolve(name + ".s").toFile(), "r")) {
            assertEquals(DownloadSourceStore.MAGIC, file.readInt());
            int headerLength = file.readInt();
            file.seek(file.getFilePointer() + headerLength);
            byte[] bytes = new byte[file.readInt()];
            file.readFully(bytes);
            MessagePak record = MessagePak.fromBytes(bytes);
            assertArrayEquals(cid(1).bytes(), record.getByteArray("/cid").orElseThrow());
            assertEquals(List.of("cid"), record.list("/"));
        }
        try (var files = Files.list(directory)) {
            assertEquals(1, files.count());
        }
    }
}

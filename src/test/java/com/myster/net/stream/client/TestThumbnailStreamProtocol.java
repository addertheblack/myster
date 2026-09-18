package com.myster.net.stream.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.myster.filemanager.FileItem;
import com.myster.filemanager.FileTypeListManager;
import com.myster.mml.MessagePak;
import com.myster.net.MysterSocket;
import com.myster.net.client.MysterStream;
import com.myster.net.server.ConnectionContext;
import com.myster.net.stream.client.msdownload.MSDownloadLocalQueue;
import com.myster.net.stream.server.ServerStreamHandler;
import com.myster.net.stream.server.ThumbnailStreamServer;
import com.myster.type.MysterType;

class TestThumbnailStreamProtocol {
    private static final MysterType TYPE = new MysterType(new byte[16]);
    private final MysterStream stream = new MysterStreamImpl(mock(MSDownloadLocalQueue.class));

    @Test
    void facadeWritesOnlyThreeFieldsAfterSectionAcknowledgement() throws Exception {
        ByteArrayOutputStream request = new ByteArrayOutputStream();
        MessagePak empty = MessagePak.newEmpty();
        ByteArrayOutputStream reply = new ByteArrayOutputStream();
        reply.write(1);
        new MysterDataOutputStream(reply).writeMessagePack(empty);
        InputStream input = new ByteArrayInputStream(reply.toByteArray()) {
            @Override
            public synchronized int read() {
                if (pos == 0) {
                    assertEquals(4, request.size(), "request must wait for the acknowledgement");
                }
                return super.read();
            }
        };
        MysterSocket socket = socket(input, request);
        assertNull(stream.getThumbnail(socket, TYPE, "été-写真.mp4", 256));
        var sent = new MysterDataInputStream(new ByteArrayInputStream(request.toByteArray()));
        assertEquals(79, sent.readInt());
        MessagePak body = sent.readMessagePack();
        assertEquals(3, body.list("/").size());
        assertArrayEquals(TYPE.toBytes(), body.getByteArray("/type").orElseThrow());
        assertEquals("été-写真.mp4", body.getString("/filename").orElseThrow());
        assertEquals(256, body.getInt("/size").orElseThrow());
        assertEquals(-1, sent.read());
        verify(socket, never()).close();
    }

    @Test
    void oldServerRejectionIsNotAThumbnailMissAndSendsNoRequest() {
        ByteArrayOutputStream request = new ByteArrayOutputStream();
        MysterSocket socket = socket(new ByteArrayInputStream(new byte[] {0}), request);
        assertThrows(UnknownProtocolException.class,
                () -> stream.getThumbnail(socket, TYPE, "file", 32));
        assertEquals(4, request.size());
    }

    @Test
    void invalidLocalArgumentsWriteNoSectionBytes() {
        ByteArrayOutputStream request = new ByteArrayOutputStream();
        MysterSocket socket = socket(new ByteArrayInputStream(new byte[0]), request);
        for (int size : new int[] {-1, 0, 257, 1024, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class,
                    () -> stream.getThumbnail(socket, TYPE, "file", size));
        }
        assertThrows(IllegalArgumentException.class,
                () -> stream.getThumbnail(socket, TYPE, "界".repeat(22000), 32));
        assertThrows(IllegalArgumentException.class,
                () -> stream.getThumbnail(socket, TYPE, "", 32));
        assertThrows(IllegalArgumentException.class,
                () -> stream.getThumbnail(socket, TYPE, null, 32));
        assertThrows(IllegalArgumentException.class,
                () -> stream.getThumbnail(socket, null, "file", 32));
        assertEquals(0, request.size());
    }

    @Test
    void malformedReplyIsAnIoFailure() throws Exception {
        ByteArrayOutputStream reply = new ByteArrayOutputStream();
        reply.write(1);
        MessagePak header = MessagePak.newEmpty();
        header.putString("/mimeType", "image/jpeg");
        new MysterDataOutputStream(reply).writeMessagePack(header);
        MysterSocket socket = socket(new ByteArrayInputStream(reply.toByteArray()), new ByteArrayOutputStream());
        assertThrows(IOException.class, () -> stream.getThumbnail(socket, TYPE, "file", 32));
    }

    @Test
    @Timeout(10)
    void repeatedImagesAndMissLeaveConnectionReadyForAnotherSection() throws Exception {
        BufferedImage png = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        png.setRGB(0, 0, 0x80402010);
        BufferedImage raw = new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB);
        raw.setRGB(0, 0, 0xff123456);
        raw.setRGB(1, 0, 0x80654321);
        Map<Path, BufferedImage> images = Map.of(Path.of("png"), png, Path.of("raw"), raw);
        FileTypeListManager files = mock(FileTypeListManager.class);
        when(files.isShared(TYPE)).thenReturn(true);
        for (Path path : images.keySet()) {
            FileItem item = mock(FileItem.class);
            when(item.getPath()).thenReturn(path);
            when(files.getFileItem(TYPE, path.toString())).thenReturn(item);
        }
        ThumbnailStreamServer thumbnails = new ThumbnailStreamServer(_ -> Optional.empty(),
                (path, _) -> images.get(path));
        ServerStreamHandler nextSection = new ServerStreamHandler() {
            @Override
            public int getSectionNumber() { return 101; }

            @Override
            public void section(ConnectionContext context) throws IOException {
                MessagePak stats = MessagePak.newEmpty();
                stats.putString("/name", "after thumbnails");
                context.socket().out.writeMessagePack(stats);
                context.socket().out.flush();
            }
        };
        try (var serverInput = new PipedInputStream(8192);
             var clientOutput = new PipedOutputStream(serverInput);
             var clientInput = new PipedInputStream(8192);
             var serverOutput = new PipedOutputStream(clientInput)) {
            MysterSocket client = socket(clientInput, new BufferedOutputStream(clientOutput));
            MysterSocket server = socket(serverInput, new BufferedOutputStream(serverOutput));
            var context = new ConnectionContext(server, null, null, null, files, Optional.empty());
            FutureTask<Void> serving = new FutureTask<>(() -> {
                for (int i = 0; i < 5; i++) {
                    int section = server.in.readInt();
                    if (section == ThumbnailStreamServer.NUMBER) {
                        thumbnails.doSection(context);
                    } else {
                        assertEquals(nextSection.getSectionNumber(), section);
                        nextSection.doSection(context);
                    }
                }
                return null;
            });
            Thread worker = Thread.ofVirtual().start(serving);
            try {
                assertImage(png, stream.getThumbnail(client, TYPE, "png", 64));
                assertImage(raw, stream.getThumbnail(client, TYPE, "raw", 64));
                assertNull(stream.getThumbnail(client, TYPE, "missing", 64));
                assertImage(png, stream.getThumbnail(client, TYPE, "png", 64));
                assertEquals("after thumbnails", stream.getServerStats(client).getString("/name").orElseThrow());
                serving.get(5, TimeUnit.SECONDS);
                verify(client, never()).close();
                verify(server, never()).close();
            } finally {
                worker.interrupt();
            }
        }
    }

    private static MysterSocket socket(InputStream in, OutputStream out) {
        return mock(MysterSocket.class, withSettings().useConstructor(
                new MysterDataInputStream(in), new MysterDataOutputStream(out)));
    }

    private static void assertImage(BufferedImage expected, BufferedImage actual) {
        assertEquals(expected.getWidth(), actual.getWidth());
        assertEquals(expected.getHeight(), actual.getHeight());
        int width = expected.getWidth();
        int height = expected.getHeight();
        assertArrayEquals(expected.getRGB(0, 0, width, height, null, 0, width),
                          actual.getRGB(0, 0, width, height, null, 0, width));
    }
}

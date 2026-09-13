package com.myster.net.stream.client.msdownload;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.myster.cid.ServerCid;
import com.myster.hash.FileHash;
import com.myster.hash.SimpleFileHash;
import com.myster.mml.MessagePak;
import com.myster.net.MysterAddress;
import com.myster.net.client.MysterStream;
import com.myster.net.client.ParamBuilder;
import com.myster.net.stream.client.MysterDataInputStream;
import com.myster.net.stream.client.MysterDataOutputStream;
import com.myster.search.MysterFileStub;
import com.myster.type.MysterType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestSegmentSourceRecovery {
    private MysterStream stream;
    private Controller controller;
    private PublicKey key;
    private MysterFileStub stub;
    private final FileHash[] hashes = {SimpleFileHash.buildFromHexString("md5", "00".repeat(16))};

    @BeforeEach void setup() throws Exception {
        stream = mock(MysterStream.class);
        controller = mock(Controller.class);
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(1024);
        key = generator.generateKeyPair().getPublic();
        stub = new MysterFileStub(MysterAddress.createMysterAddress("127.0.0.1:6669"),
                new MysterType(new byte[16]), "old-name");
        when(controller.claimSource(any(), any())).thenReturn(true);
        when(controller.getNextWorkSegment(anyInt())).thenReturn(new WorkSegment(0, 2), new WorkSegment(0, 0));
    }

    private Socket socket(boolean withData) throws IOException {
        ByteArrayOutputStream incoming = new ByteArrayOutputStream();
        MysterDataOutputStream out = new MysterDataOutputStream(incoming);
        out.writeByte(1); // Download section supported.
        out.writeByte(1); // File accepted.
        if (withData) {
            MessagePak queue = MessagePak.newEmpty();
            // Use the protocol constant to keep the queue frame identical to the server's.
            queue.putInt(com.myster.net.stream.server.MultiSourceSender.QUEUED_PATH, 0);
            out.writeMessagePack(queue);
            out.writeInt(6669);
            out.writeByte('d');
            out.writeLong(2);
            out.write(new byte[] {7, 8});
        }
        return new Socket(incoming.toByteArray());
    }

    private void run(Socket socket) throws IOException {
        when(stream.makeStreamConnection(any(ParamBuilder.class))).thenReturn(socket);
        new InternalSegmentDownloader(controller, stream,
                DownloadTarget.forHash(stub.getMysterAddress(), stub.getType(), Optional.of(key)), hashes, 1).run();
    }

    @Test void pinsTlsQueriesHashUsesReturnedNameAndReportsOnlyFirstWrittenBlock() throws Exception {
        Socket socket = socket(true);
        when(stream.getFileFromHash(socket, stub.getType(), hashes)).thenReturn("current-name");
        run(socket);
        var parameters = org.mockito.ArgumentCaptor.forClass(ParamBuilder.class);
        verify(stream).makeStreamConnection(parameters.capture());
        assertEquals(Optional.of(key), parameters.getValue().getExpectedServerPublicKey());
        var order = inOrder(stream, controller);
        order.verify(stream).makeStreamConnection(any(ParamBuilder.class));
        order.verify(stream).getFileFromHash(socket, stub.getType(), hashes);
        order.verify(controller).getNextWorkSegment(anyInt());
        verify(stream, never()).getFileStats(any(), any());
        verify(controller).receiveDataBlock(any(), any(), eq(Optional.of(ServerCid.fromPublicKey(key))));
        verify(controller).receiveDataBlock(any(), any(), eq(Optional.empty()));
        MysterDataInputStream sent = new MysterDataInputStream(new ByteArrayInputStream(socket.sent.toByteArray()));
        assertEquals(com.myster.net.stream.server.MultiSourceSender.SECTION_NUMBER, sent.readInt());
        assertEquals(stub.getType(), sent.readType());
        assertEquals("current-name", sent.readUTF());
        assertTrue(socket.closed);
    }

    @Test void resolvedServerWithoutHashNeverReceivesDownloadRequest() throws Exception {
        Socket socket = socket(false);
        when(stream.getFileFromHash(socket, stub.getType(), hashes)).thenReturn("");
        run(socket);
        assertEquals(0, socket.sent.size());
        verify(controller, never()).receiveDataBlock(any(), any(), any());
        assertTrue(socket.closed);
    }

    @Test void headerOnlyConnectionDoesNotReportASupplyingSource() throws Exception {
        Socket socket = socket(false);
        when(stream.getFileFromHash(socket, stub.getType(), hashes)).thenReturn("current-name");
        run(socket);
        verify(controller, never()).receiveDataBlock(any(), any(), any());
    }

    @Test void keyMismatchFailsBeforeFileQuery() throws Exception {
        when(stream.makeStreamConnection(any(ParamBuilder.class))).thenThrow(new IOException("TLS key mismatch"));
        new InternalSegmentDownloader(controller, stream,
                DownloadTarget.forHash(stub.getMysterAddress(), stub.getType(), Optional.of(key)), hashes, 1).run();
        verify(stream, never()).getFileFromHash(any(), any(), any());
        verify(controller, never()).receiveDataBlock(any(), any(), any());
    }

    @Test void socketReturnedAfterCancellationIsClosedWithoutQueryingFile() throws Exception {
        Socket socket = socket(true);
        AtomicReference<InternalSegmentDownloader> reference = new AtomicReference<>();
        when(stream.makeStreamConnection(any(ParamBuilder.class))).thenAnswer(_ -> {
            reference.get().flagToEnd();
            return socket;
        });
        InternalSegmentDownloader downloader = new InternalSegmentDownloader(controller, stream,
                DownloadTarget.forHash(stub.getMysterAddress(), stub.getType(), Optional.of(key)), hashes, 1);
        reference.set(downloader);
        downloader.run();
        verify(stream, never()).getFileFromHash(any(), any(), any());
        assertTrue(socket.closed);
    }

    @Test void knownFilenameWithoutHashesUsesLegacyDownloadPath() throws Exception {
        Socket socket = socket(true);
        when(stream.makeStreamConnection(any(ParamBuilder.class))).thenReturn(socket);
        new InternalSegmentDownloader(controller, stream, DownloadTarget.knownFile(stub),
                new FileHash[0], 1).run();
        verify(stream, never()).getFileFromHash(any(), any(), any());
        MysterDataInputStream sent = new MysterDataInputStream(new ByteArrayInputStream(socket.sent.toByteArray()));
        assertEquals(com.myster.net.stream.server.MultiSourceSender.SECTION_NUMBER, sent.readInt());
        assertEquals(stub.getType(), sent.readType());
        assertEquals(stub.getName(), sent.readUTF());
    }

    @Test void unknownFilenameWithoutHashesFailsBeforeConnectingAndHasNoPlaceholderInEndEvent() throws Exception {
        InternalSegmentDownloader downloader = new InternalSegmentDownloader(controller, stream,
                DownloadTarget.forHash(stub.getMysterAddress(), stub.getType(), Optional.of(key)),
                new FileHash[0], 1);
        SegmentDownloaderListener listener = mock(SegmentDownloaderListener.class);
        downloader.addListener(listener);
        downloader.run();
        verifyNoInteractions(stream);
        javax.swing.SwingUtilities.invokeAndWait(() -> {});
        var event = org.mockito.ArgumentCaptor.forClass(SegmentDownloaderEvent.class);
        verify(listener).endConnection(event.capture());
        assertNull(event.getValue().getMysterFileStub());
        verify(listener, never()).connected(any());
    }

    @Test void hashLookupTimeoutClosesAndRemovesDownloader() throws Exception {
        Socket socket = socket(false);
        when(stream.makeStreamConnection(any(ParamBuilder.class))).thenReturn(socket);
        when(stream.getFileFromHash(socket, stub.getType(), hashes))
                .thenThrow(new java.net.SocketTimeoutException("hash lookup read timed out"));
        InternalSegmentDownloader downloader = new InternalSegmentDownloader(controller, stream,
                DownloadTarget.forHash(stub.getMysterAddress(), stub.getType(), Optional.of(key)), hashes, 1);
        downloader.run();
        assertTrue(socket.closed);
        assertTrue(downloader.isDead());
        assertEquals(0, socket.sent.size());
        verify(controller).removeDownload(downloader);
        verify(controller, never()).receiveDataBlock(any(), any(), any());
    }

    private class Socket extends FakeMysterSocket {
        final ByteArrayOutputStream sent;
        boolean closed;
        Socket(byte[] incoming) {
            this(incoming, new ByteArrayOutputStream());
        }
        private Socket(byte[] incoming, ByteArrayOutputStream sent) {
            super(new MysterDataInputStream(new ByteArrayInputStream(incoming)), new MysterDataOutputStream(sent));
            this.sent = sent;
        }
        @Override public Optional<PublicKey> getAuthenticatedPeerKey() { return Optional.of(key); }
        @Override public void close() { closed = true; }
    }
}

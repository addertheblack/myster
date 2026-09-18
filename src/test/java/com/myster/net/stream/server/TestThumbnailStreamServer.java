package com.myster.net.stream.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.myster.access.AccessList;
import com.myster.access.AccessListReader;
import com.myster.access.AccessListState;
import com.myster.access.Policy;
import com.myster.cid.ServerCid;
import com.myster.filemanager.FileItem;
import com.myster.filemanager.FileTypeListManager;
import com.myster.mml.MessagePak;
import com.myster.net.MysterSocket;
import com.myster.net.server.ConnectionContext;
import com.myster.net.stream.ThumbnailProtocolUtils;
import com.myster.net.stream.client.MysterDataInputStream;
import com.myster.net.stream.client.MysterDataOutputStream;
import com.myster.type.MysterType;

class TestThumbnailStreamServer {
    private static final MysterType TYPE = new MysterType(new byte[16]);
    private static final ServerCid MEMBER = new ServerCid(new byte[16]);
    private static final ServerCid STRANGER = MEMBER.plusPowerOfTwo(0);
    private static final String FILENAME = "reference.jpg";
    private static final Path LOCAL_PATH = Path.of("/shared/local-file.jpg");

    private final AccessListReader access = mock(AccessListReader.class);
    private final FileTypeListManager files = mock(FileTypeListManager.class);
    private final FileItem file = mock(FileItem.class);
    private final ThumbnailStreamServer.ThumbnailSource source = mock(ThumbnailStreamServer.ThumbnailSource.class);
    private final ThumbnailStreamServer server = new ThumbnailStreamServer(access, source);
    private final ByteArrayOutputStream response = new ByteArrayOutputStream();

    @BeforeEach
    void setUp() throws Exception {
        when(access.loadAccessList(TYPE)).thenReturn(Optional.empty());
        when(files.isShared(TYPE)).thenReturn(true);
        when(files.getFileItem(TYPE, FILENAME)).thenReturn(file);
        when(file.getPath()).thenReturn(LOCAL_PATH);
        when(source.load(LOCAL_PATH, 64)).thenReturn(new BufferedImage(32, 16, BufferedImage.TYPE_INT_ARGB));
    }

    @Test
    void publicRequestChecksAccessThenIndexThenSourceAndAcknowledgesOnce() throws Exception {
        run(request(FILENAME), Optional.empty());
        assertEquals(79, server.getSectionNumber());
        MysterDataInputStream in = responseAfterAck();
        BufferedImage image = ThumbnailProtocolUtils.readResponse(in, 64);
        assertEquals(32, image.getWidth());
        assertEquals(16, image.getHeight());
        assertEquals(-1, in.read());
        var order = inOrder(access, files, source);
        order.verify(access).loadAccessList(TYPE);
        order.verify(files).isShared(TYPE);
        order.verify(files).getFileItem(TYPE, FILENAME);
        order.verify(source).load(LOCAL_PATH, 64);
    }

    @Test
    void publicPolicyAllowsAnonymousCaller() throws Exception {
        accessPolicy(true);
        run(request(FILENAME), Optional.empty());
        assertEquals(32, ThumbnailProtocolUtils.readResponse(responseAfterAck(), 64).getWidth());
    }

    @Test
    void verifiedPrivateMemberCanFetchThumbnail() throws Exception {
        AccessListState state = accessPolicy(false);
        when(state.isMember(MEMBER)).thenReturn(true);
        run(request(FILENAME), Optional.of(MEMBER));
        assertEquals(16, ThumbnailProtocolUtils.readResponse(responseAfterAck(), 64).getHeight());
        verify(state).isMember(MEMBER);
    }

    @Test
    void deniedPrivateRequestCannotReachFilesOrSource() throws Exception {
        AccessListState state = accessPolicy(false);
        when(state.isMember(MEMBER)).thenReturn(true);
        for (Optional<ServerCid> caller : java.util.List.of(Optional.<ServerCid>empty(), Optional.of(STRANGER))) {
            response.reset();
            MessagePak request = request(FILENAME);
            request.putByteArray("/callerCid", MEMBER.bytes());
            run(request, caller);
            assertMiss();
        }
        verifyNoInteractions(files, source);
        verify(state, never()).isMember(MEMBER);
    }

    @Test
    void unsharedOrUnknownTypeCannotReachFileLookupOrSource() throws Exception {
        when(files.isShared(TYPE)).thenReturn(false);
        run(request(FILENAME), Optional.empty());
        assertMiss();
        verify(files, never()).getFileItem(TYPE, FILENAME);
        verifyNoInteractions(source);
    }

    @Test
    void missingOrPathLikeReferencesOnlyConsultTheSharedIndex() throws Exception {
        for (String filename : new String[] {"missing.jpg", "../../private.jpg", "/etc/passwd"}) {
            response.reset();
            run(request(filename), Optional.empty());
            assertMiss();
            verify(files).getFileItem(TYPE, filename);
        }
        verifyNoInteractions(source);
    }

    @Test
    void unavailableThumbnailMatchesTheMissingResponse() throws Exception {
        when(source.load(LOCAL_PATH, 64)).thenReturn(null);
        run(request(FILENAME), Optional.empty());
        assertMiss();
    }

    @Test
    void malformedRequestsStopBeforeAccessOrAcquisition() throws Exception {
        MessagePak badSize = request(FILENAME);
        badSize.putInt("/size", 257);
        MessagePak badType = request(FILENAME);
        badType.putByteArray("/type", new byte[15]);
        for (byte[] bytes : new byte[][] {frame(badSize), frame(badType),
                ByteBuffer.allocate(4).putInt(ThumbnailProtocolUtils.MAX_REQUEST_BYTES + 1).array(),
                ByteBuffer.allocate(4).putInt(-1).array(), new byte[0]}) {
            response.reset();
            assertThrows(IOException.class, () -> runBytes(bytes, Optional.empty()));
            assertEquals(1, response.size(), "only the inherited acknowledgement may be written");
        }
        verifyNoInteractions(access, files, source);
    }

    @Test
    void interruptedAcquisitionPreservesFlagAndWritesNoResponseHeader() throws Exception {
        when(source.load(LOCAL_PATH, 64)).thenThrow(new InterruptedException("cancelled"));
        assertFalse(Thread.currentThread().isInterrupted());
        try {
            InterruptedIOException exception = assertThrows(InterruptedIOException.class,
                    () -> run(request(FILENAME), Optional.empty()));
            assertTrue(exception.getCause() instanceof InterruptedException);
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(1, response.size());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void unexpectedSourceFailureIsNotReportedAsAMiss() throws Exception {
        when(source.load(LOCAL_PATH, 64)).thenThrow(new IllegalStateException("provider bug"));
        assertThrows(IllegalStateException.class, () -> run(request(FILENAME), Optional.empty()));
        assertEquals(1, response.size());
    }

    private AccessListState accessPolicy(boolean isPublic) throws IOException {
        AccessList list = mock(AccessList.class);
        AccessListState state = mock(AccessListState.class);
        when(access.loadAccessList(TYPE)).thenReturn(Optional.of(list));
        when(list.getState()).thenReturn(state);
        when(state.getPolicy()).thenReturn(new Policy(isPublic));
        return state;
    }

    private void run(MessagePak request, Optional<ServerCid> caller) throws IOException {
        runBytes(frame(request), caller);
    }

    private void runBytes(byte[] request, Optional<ServerCid> caller) throws IOException {
        MysterSocket socket = mock(MysterSocket.class, withSettings().useConstructor(
                new MysterDataInputStream(new ByteArrayInputStream(request)),
                new MysterDataOutputStream(response)));
        server.doSection(new ConnectionContext(socket, null, null, null, files, caller));
        verify(socket, never()).close();
    }

    private MysterDataInputStream responseAfterAck() throws IOException {
        var in = new MysterDataInputStream(new ByteArrayInputStream(response.toByteArray()));
        assertEquals(1, in.readUnsignedByte());
        return in;
    }

    private void assertMiss() throws IOException {
        var in = responseAfterAck();
        assertTrue(in.readMessagePack().list("/").isEmpty());
        assertEquals(-1, in.read());
        assertNull(ThumbnailProtocolUtils.readResponse(responseAfterAck(), 64));
    }

    private static MessagePak request(String filename) throws IOException {
        return ThumbnailProtocolUtils.createRequest(TYPE, filename, 64);
    }

    private static byte[] frame(MessagePak request) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new MysterDataOutputStream(bytes).writeMessagePack(request);
        return bytes.toByteArray();
    }
}

package com.myster.type.join;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.security.KeyPairGenerator;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.myster.cid.ServerCid;
import com.myster.mml.MessagePak;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.net.client.MysterStream;
import com.myster.net.server.ConnectionContext;
import com.myster.net.stream.client.MysterDataInputStream;
import com.myster.net.stream.client.MysterDataOutputStream;
import com.myster.net.stream.client.MysterStreamImpl;
import com.myster.net.stream.client.msdownload.MSDownloadLocalQueue;
import com.myster.type.MysterType;

class TestTypeJoinProtocol {
    private static final byte[] TYPE_BYTES = new byte[16];
    private static final byte[] INVITATION_ID = new byte[16];

    @Test
    void clientWritesBoundedSchemaAndPreservesFutureStatus() throws Exception {
        MessagePak response = MessagePak.newEmpty();
        response.putInt("/schemaVersion", 1);
        response.putString("/status", "TRY_LATER");
        ByteArrayOutputStream serverBytes = new ByteArrayOutputStream();
        serverBytes.write(1);
        new MysterDataOutputStream(serverBytes).writeMessagePack(response);
        TestSocket socket = new TestSocket(serverBytes.toByteArray());

        MysterStream stream = new MysterStreamImpl(mock(MSDownloadLocalQueue.class));
        TypeJoinStatus status = stream.redeemTypeInvitation(
                socket, new MysterType(TYPE_BYTES), INVITATION_ID, "secret");

        assertFalse(status.isCanonical());
        MysterDataInputStream sent = new MysterDataInputStream(
                new ByteArrayInputStream(socket.written()));
        assertEquals(TypeJoinServer.NUMBER, sent.readInt());
        MessagePak request = sent.readMessagePack(TypeJoinServer.MAX_FRAME_BYTES);
        assertEquals(1, request.getInt("/schemaVersion").orElseThrow());
        assertArrayEquals(TYPE_BYTES, request.getByteArray("/type").orElseThrow());
        assertArrayEquals(INVITATION_ID,
                request.getByteArray("/invitation").orElseThrow());
        assertEquals("secret", request.getString("/code").orElseThrow());
    }

    @Test
    void serverUsesOnlyTlsCallerIdentity() throws Exception {
        TypeInvitationManager manager = mock(TypeInvitationManager.class);
        ServerCid caller = ServerCid.fromPublicKey(
                KeyPairGenerator.getInstance("RSA").generateKeyPair().getPublic());
        when(manager.redeem(any(MysterType.class), any(byte[].class), any(char[].class),
                eq(caller), anyString())).thenReturn(TypeJoinStatus.APPROVED);
        TestSocket socket = new TestSocket(requestBytes("secret"));
        ConnectionContext context = new ConnectionContext(socket,
                new MysterAddress(InetAddress.getLoopbackAddress()), null, null, null,
                Optional.of(caller));

        new TypeJoinServer(manager).section(context);

        ArgumentCaptor<ServerCid> callerCaptor = ArgumentCaptor.forClass(ServerCid.class);
        ArgumentCaptor<char[]> codeCaptor = ArgumentCaptor.forClass(char[].class);
        verify(manager).redeem(any(MysterType.class), any(byte[].class), codeCaptor.capture(),
                callerCaptor.capture(), anyString());
        assertEquals(caller, callerCaptor.getValue());
        assertArrayEquals(new char[] {'\0', '\0', '\0', '\0', '\0', '\0'},
                codeCaptor.getValue(), "server clears its mutable password copy");

        MessagePak response = new MysterDataInputStream(
                new ByteArrayInputStream(socket.written()))
                        .readMessagePack(TypeJoinServer.MAX_FRAME_BYTES);
        assertEquals("APPROVED", response.getString("/status").orElseThrow());
    }

    @Test
    void serverRejectsPlaintextBeforeReadingOrMutating() throws Exception {
        TypeInvitationManager manager = mock(TypeInvitationManager.class);
        TestSocket socket = new TestSocket(requestBytes("secret"));
        ConnectionContext context = new ConnectionContext(socket,
                new MysterAddress(InetAddress.getLoopbackAddress()), null, null, null,
                Optional.empty());

        assertThrows(IOException.class, () -> new TypeJoinServer(manager).section(context));
        verify(manager, never()).redeem(any(), any(), any(), any(), anyString());
    }

    private static byte[] requestBytes(String code) throws IOException {
        MessagePak request = MessagePak.newEmpty();
        request.putInt("/schemaVersion", 1);
        request.putByteArray("/type", TYPE_BYTES);
        request.putByteArray("/invitation", INVITATION_ID);
        request.putString("/code", code);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new MysterDataOutputStream(bytes).writeMessagePack(request);
        return bytes.toByteArray();
    }

    private static final class TestSocket extends MysterSocket {
        private final ByteArrayOutputStream output;

        TestSocket(byte[] input) {
            this(new MysterDataInputStream(new ByteArrayInputStream(input)),
                    new ByteArrayOutputStream());
        }

        private TestSocket(MysterDataInputStream input, ByteArrayOutputStream output) {
            super(input, new MysterDataOutputStream(output));
            this.output = output;
        }

        byte[] written() { return output.toByteArray(); }
        public InetAddress getInetAddress() { return InetAddress.getLoopbackAddress(); }
        public InetAddress getLocalAddress() { return InetAddress.getLoopbackAddress(); }
        public int getPort() { return 6669; }
        public int getLocalPort() { return 6669; }
        public MysterDataInputStream getInputStream() { return in; }
        public MysterDataOutputStream getOutputStream() { return out; }
        public void setSoLinger(boolean on, int value) throws SocketException {}
        public int getSoLinger() { return -1; }
        public void setSoTimeout(int timeout) throws SocketException {}
        public int getSoTimeout() { return 0; }
        public void close() {}
        public String toString() { return "test socket"; }
    }
}

package com.myster.net.stream.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.myster.mml.MessagePak;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.net.client.ParamBuilder;
import com.myster.net.stream.client.msdownload.MSDownloadLocalQueue;
import com.myster.net.stream.server.ServerStats;

class TestMysterStreamImpl {
    private final MysterAddress address = new MysterAddress(InetAddress.getLoopbackAddress());
    private final PublicKey key = KeyPairGenerator.getInstance("RSA").generateKeyPair().getPublic();
    private final MysterSocket socket = mock(MysterSocket.class);
    private MockedStatic<MysterSocketFactory> connections;

    TestMysterStreamImpl() throws Exception {}

    @BeforeEach
    void mockSocketFactory() {
        connections = mockStatic(MysterSocketFactory.class);
    }

    @AfterEach
    void closeSocketFactoryMock() {
        connections.close();
    }

    private MysterStreamImpl stream(Optional<PublicKey> cached) {
        return spy(new MysterStreamImpl(mock(MSDownloadLocalQueue.class), _ -> cached));
    }

    private MessagePak stats(PublicKey publicKey) {
        MessagePak stats = MessagePak.newEmpty();
        stats.putByteArray(ServerStats.IDENTITY, publicKey.getEncoded());
        return stats;
    }

    @Test
    void explicitKeyIsAuthoritativeAndDoesNotFetchStats() throws Exception {
        PublicKey other = KeyPairGenerator.getInstance("RSA").generateKeyPair().getPublic();
        MysterStreamImpl stream = stream(Optional.of(other));
        connections.when(() -> MysterSocketFactory.makeStreamConnection(address, key)).thenReturn(socket);

        assertSame(socket, stream.makeStreamConnection(new ParamBuilder(address).withExpectedServerPublicKey(key)));
        verify(stream, never()).getServerStats(any());
        verify(socket, never()).close();
    }

    @Test
    void cachedKeyOpensPinnedConnectionWithoutStats() throws Exception {
        MysterStreamImpl stream = stream(Optional.of(key));
        connections.when(() -> MysterSocketFactory.makeStreamConnection(address, key)).thenReturn(socket);

        assertSame(socket, stream.makeStreamConnection(address));
        verify(stream, never()).getServerStats(any());
    }

    @Test
    void cacheMissReadsStatsAndReturnsSameVerifiedSocketWithoutChangingTimeout() throws Exception {
        MysterStreamImpl stream = stream(Optional.empty());
        connections.when(() -> MysterSocketFactory.makeStreamConnection(address)).thenReturn(socket);
        when(socket.getAuthenticatedPeerKey()).thenReturn(Optional.of(key));
        doReturn(stats(key)).when(stream).getServerStats(socket);

        assertSame(socket, stream.makeStreamConnection(address));
        verify(stream, times(1)).getServerStats(socket);
        verify(socket, never()).setSoTimeout(anyInt());
        verify(socket, never()).close();
        connections.verify(() -> MysterSocketFactory.makeStreamConnection(address), times(1));

        connections.when(() -> MysterSocketFactory.makeStreamConnection(address, key)).thenReturn(socket);
        assertSame(socket, stream.makeStreamConnection(address));
        verify(stream, times(1)).getServerStats(socket);
        connections.verify(() -> MysterSocketFactory.makeStreamConnection(address, key));
    }

    @Test
    void mismatchedStatsCannotBePublishedOrReturned() throws Exception {
        MysterStreamImpl stream = stream(Optional.empty());
        connections.when(() -> MysterSocketFactory.makeStreamConnection(address)).thenReturn(socket);
        when(socket.getAuthenticatedPeerKey()).thenReturn(Optional.of(key));
        doReturn(stats(KeyPairGenerator.getInstance("RSA").generateKeyPair().getPublic()))
                .when(stream).getServerStats(socket);

        assertThrows(IOException.class, () -> stream.makeStreamConnection(address));
        verify(socket).close();

        MysterSocket retry = mock(MysterSocket.class);
        connections.when(() -> MysterSocketFactory.makeStreamConnection(address)).thenReturn(retry);
        when(retry.getAuthenticatedPeerKey()).thenReturn(Optional.of(key));
        doReturn(stats(key)).when(stream).getServerStats(retry);
        assertSame(retry, stream.makeStreamConnection(address));
        connections.verify(() -> MysterSocketFactory.makeStreamConnection(address), times(2));
        verify(retry, never()).close();
    }

    @Test
    void statsTimeoutClosesSocketWithoutChangingTimeout() throws Exception {
        MysterStreamImpl stream = stream(Optional.empty());
        connections.when(() -> MysterSocketFactory.makeStreamConnection(address)).thenReturn(socket);
        SocketTimeoutException timeout = new SocketTimeoutException("stats timed out");
        doThrow(timeout).when(stream).getServerStats(socket);

        assertSame(timeout, assertThrows(SocketTimeoutException.class, () -> stream.makeStreamConnection(address)));
        verify(socket, never()).setSoTimeout(anyInt());
        verify(socket).close();
    }

    @Test
    void serverStatsRetainsConnectionTimeout() throws Exception {
        ByteArrayOutputStream reply = new ByteArrayOutputStream();
        reply.write(1);
        new MysterDataOutputStream(reply).writeMessagePack(stats(key));
        MysterSocket statsSocket = mock(MysterSocket.class, withSettings().useConstructor(
                new MysterDataInputStream(new ByteArrayInputStream(reply.toByteArray())),
                new MysterDataOutputStream(new ByteArrayOutputStream())));

        MessagePak response = stream(Optional.empty()).getServerStats(statsSocket);

        assertArrayEquals(key.getEncoded(), response.getByteArray(ServerStats.IDENTITY).orElseThrow());
        verify(statsSocket, never()).setSoTimeout(anyInt());
    }

    @Test
    void keylessStatsAndUnauthenticatedTransportAreRejected() throws Exception {
        MysterStreamImpl stream = stream(Optional.empty());
        connections.when(() -> MysterSocketFactory.makeStreamConnection(address)).thenReturn(socket);
        doReturn(MessagePak.newEmpty(), stats(key)).when(stream).getServerStats(socket);
        when(socket.getAuthenticatedPeerKey()).thenReturn(Optional.empty());

        assertThrows(IOException.class, () -> stream.makeStreamConnection(address));
        assertThrows(IOException.class, () -> stream.makeStreamConnection(address));
        verify(socket, times(2)).close();
    }

    @Test
    void streamConnectionRequiresAddress() {
        assertThrows(IllegalArgumentException.class, () -> stream(Optional.empty()).makeStreamConnection(new ParamBuilder()));
    }
}

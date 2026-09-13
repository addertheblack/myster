package com.myster.net;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Optional;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.junit.jupiter.api.Test;

import com.myster.identity.Identity;

class TestTLSSocketTimeouts {
    @Test void tlsRefusalOrEofThrowsAndClosesWithoutPlaintextFallback() throws Exception {
        MysterAddress address = MysterAddress.createMysterAddress("127.0.0.1");
        for (byte[] response : new byte[][] { {0}, {17}, {} }) {
            try (var sockets = mockConstruction(Socket.class, (socket, _) -> {
                when(socket.getInputStream()).thenReturn(new ByteArrayInputStream(response));
                when(socket.getOutputStream()).thenReturn(new ByteArrayOutputStream());
            })) {
                assertThrows(IOException.class, () ->
                        TLSSocket.createClientSocket(address, mock(Identity.class), Optional.empty()));
                assertEquals(1, sockets.constructed().size(), "No plaintext retry should be opened");
                verify(sockets.constructed().getFirst()).close();
            }
        }
    }

    @Test void serverUpgradeProgrammingFailurePropagatesAndClosesSocket() throws Exception {
        Socket accepted = mock(Socket.class);
        assertThrows(NullPointerException.class, () -> TLSSocket.upgradeServerSocket(accepted, null));
        verify(accepted).close();
    }

    @Test void peerCertificateLookupOnlyTranslatesUnverifiedPeer() throws Exception {
        SSLSocket transport = mock(SSLSocket.class);
        when(transport.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(transport.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        var constructor = TLSSocket.class.getDeclaredConstructor(SSLSocket.class);
        constructor.setAccessible(true);
        TLSSocket socket = constructor.newInstance(transport);
        javax.net.ssl.SSLSession session = mock(javax.net.ssl.SSLSession.class);
        when(transport.getSession()).thenReturn(session);
        var unverified = new javax.net.ssl.SSLPeerUnverifiedException("not authenticated");
        when(session.getPeerCertificates()).thenThrow(unverified);
        assertSame(unverified, assertThrows(IOException.class, socket::getPeerCertificateChain).getCause());
        IllegalStateException bug = new IllegalStateException("broken session");
        when(transport.getSession()).thenThrow(bug);
        assertSame(bug, assertThrows(IllegalStateException.class, socket::getPeerCertificateChain));
    }

    @Test void tcpConnectHasExplicitDeadlineAndFailureClosesSocket() throws Exception {
        MysterAddress address = MysterAddress.createMysterAddress("127.0.0.1");
        try (var sockets = mockConstruction(Socket.class, (socket, _) ->
                doThrow(new SocketTimeoutException("connect timeout"))
                        .when(socket).connect(any(), eq(30_000)))) {
            IOException error = assertThrows(IOException.class, () ->
                    TLSSocket.createClientSocket(address, mock(Identity.class), Optional.empty()));
            assertInstanceOf(SocketTimeoutException.class, error.getCause());
            Socket socket = sockets.constructed().getFirst();
            verify(socket).connect(new InetSocketAddress(address.getInetAddress(), address.getPort()), 30_000);
            verify(socket).close();
        }
    }

    @Test void negotiationAndTlsHandshakeBothHaveReadTimeouts() throws Exception {
        MysterAddress address = MysterAddress.createMysterAddress("127.0.0.1");
        SSLContext context = mock(SSLContext.class);
        SSLSocketFactory factory = mock(SSLSocketFactory.class);
        SSLSocket tls = mock(SSLSocket.class);
        when(context.getSocketFactory()).thenReturn(factory);
        when(factory.createSocket(any(Socket.class), anyString(), anyInt(), eq(true))).thenReturn(tls);
        doThrow(new SocketTimeoutException("handshake timeout")).when(tls).startHandshake();
        try (var contexts = mockStatic(SSLContext.class);
             var keys = mockConstruction(IdentityKeyManager.class);
             var sockets = mockConstruction(Socket.class, (socket, _) -> {
                 when(socket.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[] {1}));
                 when(socket.getOutputStream()).thenReturn(new ByteArrayOutputStream());
                 when(socket.getInetAddress()).thenReturn(InetAddress.getLoopbackAddress());
             })) {
            contexts.when(() -> SSLContext.getInstance("TLS")).thenReturn(context);
            IOException error = assertThrows(IOException.class, () ->
                    TLSSocket.createClientSocket(address, mock(Identity.class), Optional.empty()));
            assertInstanceOf(SocketTimeoutException.class, error.getCause());
            Socket socket = sockets.constructed().getFirst();
            var order = inOrder(socket, tls);
            order.verify(socket).setSoTimeout(120_000);
            order.verify(tls).setSoTimeout(120_000);
            order.verify(tls).startHandshake();
            verify(socket).close();
        }
    }
}

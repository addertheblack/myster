package com.myster.client.ui;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.List;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.general.thread.PromiseFuture;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.net.client.MysterDatagram;
import com.myster.net.client.MysterProtocol;
import com.myster.net.client.MysterStream;
import com.myster.net.client.ParamBuilder;
import com.myster.type.MysterType;

class TestTypeListerThread {
    @Test
    void typeListingPinsUdpAndFallbackAndCompletesEmptyResults() throws Exception {
        PublicKey key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic();
        MysterAddress address = MysterAddress.createMysterAddress("127.0.0.1:7777");

        for (boolean fallback : List.of(false, true)) {
            var protocol = mock(MysterProtocol.class);
            var datagram = mock(MysterDatagram.class);
            var stream = mock(MysterStream.class);
            var socket = mock(MysterSocket.class);
            var listener = mock(TypeListerThread.TypeListener.class);
            when(protocol.getDatagram()).thenReturn(datagram);
            when(protocol.getStream()).thenReturn(stream);
            when(datagram.getTypes(any())).thenReturn(fallback
                    ? PromiseFuture.newPromiseFutureException(new IOException("UDP unavailable"))
                    : PromiseFuture.newPromiseFuture(new MysterType[0]));
            when(stream.makeStreamConnection(any(ParamBuilder.class))).thenReturn(socket);
            when(stream.getTypes(socket)).thenReturn(new MysterType[0]);

            new TypeListerThread(protocol, listener, _ -> {}, address, null, null, key).run();
            SwingUtilities.invokeAndWait(() -> {});

            var udp = ArgumentCaptor.forClass(ParamBuilder.class);
            verify(datagram).getTypes(udp.capture());
            assertEquals(address, udp.getValue().getAddress().orElseThrow());
            assertEquals(key, udp.getValue().getExpectedServerPublicKey().orElseThrow());
            verify(listener).refreshIP(address);
            verify(listener).finished();
            verify(listener, never()).failed(any());

            if (fallback) {
                var tcp = ArgumentCaptor.forClass(ParamBuilder.class);
                verify(stream).makeStreamConnection(tcp.capture());
                assertEquals(address, tcp.getValue().getAddress().orElseThrow());
                assertEquals(key, tcp.getValue().getExpectedServerPublicKey().orElseThrow());
                verify(stream, never()).makeStreamConnection(any(MysterAddress.class));
                verify(socket).close();
            } else {
                verifyNoInteractions(stream, socket);
            }
        }
    }
}

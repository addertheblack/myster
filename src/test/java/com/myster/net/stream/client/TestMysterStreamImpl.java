package com.myster.net.stream.client;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.net.client.ParamBuilder;
import com.myster.net.stream.client.msdownload.MSDownloadLocalQueue;

class TestMysterStreamImpl {
    @Test
    void commonParametersCarryExpectedKeyIntoConnectionSetup() throws Exception {
        MysterAddress address = new MysterAddress(InetAddress.getLoopbackAddress());
        PublicKey expectedKey = KeyPairGenerator.getInstance("RSA")
                .generateKeyPair().getPublic();
        MysterSocket socket = mock(MysterSocket.class);
        MysterStreamImpl.ConnectionFactory connections = mock(
                MysterStreamImpl.ConnectionFactory.class);
        when(connections.open(address, Optional.of(expectedKey))).thenReturn(socket);
        MysterStreamImpl stream = new MysterStreamImpl(
                mock(MSDownloadLocalQueue.class), connections);

        MysterSocket result = stream.makeStreamConnection(
                new ParamBuilder(address).withExpectedServerPublicKey(expectedKey));

        assertSame(socket, result);
        verify(connections).open(address, Optional.of(expectedKey));
    }

    @Test
    void addressConvenienceConnectionHasNoIndependentExpectedKey() throws Exception {
        MysterAddress address = new MysterAddress(InetAddress.getLoopbackAddress());
        MysterSocket socket = mock(MysterSocket.class);
        MysterStreamImpl.ConnectionFactory connections = mock(
                MysterStreamImpl.ConnectionFactory.class);
        when(connections.open(address, Optional.empty())).thenReturn(socket);
        MysterStreamImpl stream = new MysterStreamImpl(
                mock(MSDownloadLocalQueue.class), connections);

        assertSame(socket, stream.makeStreamConnection(address));
        verify(connections).open(address, Optional.empty());
    }

    @Test
    void streamConnectionRequiresAddress() {
        MysterStreamImpl stream = new MysterStreamImpl(
                mock(MSDownloadLocalQueue.class), mock(MysterStreamImpl.ConnectionFactory.class));

        assertThrows(IllegalArgumentException.class,
                () -> stream.makeStreamConnection(new ParamBuilder()));
    }
}

package com.myster.client.ui;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.io.IOException;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.Optional;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.myster.mml.MessagePak;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.net.client.*;
import com.myster.type.MysterType;

class TestFileInfoListerThread {
    @Test void fileStatsUsePinnedStreams() throws Exception {
        MysterType type = new MysterType(new byte[16]);
        PublicKey key=KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic();
        var protocol=mock(MysterProtocol.class);
        var stream=mock(MysterStream.class);
        var datagram=mock(MysterDatagram.class);
        var socket=mock(MysterSocket.class);
        when(protocol.getStream()).thenReturn(stream);
        when(protocol.getDatagram()).thenReturn(datagram);
        when(stream.makeStreamConnection(any(ParamBuilder.class))).thenReturn(socket);
        when(stream.getFileStats(eq(socket),any())).thenReturn(MessagePak.newEmpty());
        var address = MysterAddress.createMysterAddress("127.0.0.1");
        new FileInfoListerThread(protocol,_ -> {},_ -> {},address,type,"file",key).run();
        verifyNoInteractions(datagram);
        var params=ArgumentCaptor.forClass(ParamBuilder.class);
        verify(stream).makeStreamConnection(params.capture());
        assertEquals(Optional.of(address), params.getValue().getAddress());
        assertEquals(Optional.of(key),params.getValue().getExpectedServerPublicKey());
        verify(socket).close();
    }

    @Test void connectionFailureReportsAnErrorWithoutAnUnpinnedRetry() throws Exception {
        var protocol = mock(MysterProtocol.class);
        var stream = mock(MysterStream.class);
        when(protocol.getStream()).thenReturn(stream);
        when(stream.makeStreamConnection(any(ParamBuilder.class)))
                .thenThrow(new IOException("Server key mismatch"));
        var listener = mock(FileInfoListerThread.FileStatsListener.class);
        var messages = mock(com.myster.util.Sayable.class);
        var address = MysterAddress.createMysterAddress("127.0.0.1");
        var key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic();

        new FileInfoListerThread(protocol, listener, messages, address,
                new MysterType(new byte[16]), "file", key).run();
        SwingUtilities.invokeAndWait(() -> {});

        verify(stream, times(1)).makeStreamConnection(any(ParamBuilder.class));
        verify(protocol, never()).getDatagram();
        verifyNoInteractions(listener);
        verify(messages).say("Transmission error could not get File Stats.");
    }
}

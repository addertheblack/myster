package com.myster.client.ui;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.myster.mml.MessagePak;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.net.client.*;
import com.myster.net.stream.client.MysterDataInputStream;
import com.myster.net.stream.client.MysterDataOutputStream;
import com.myster.type.MysterType;
import com.myster.util.Sayable;

class TestFileListerThread {
    @Test void emptyFileListHasATerminalCallbackAndFailureDoesNotLookLikeSuccess() throws Exception {
        MysterType type = new MysterType(new byte[16]);
        PublicKey key=KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic();
        var bytes=new ByteArrayOutputStream();
        var output=new DataOutputStream(bytes); output.writeByte(1); output.writeInt(0);
        var socket=mock(MysterSocket.class,withSettings().useConstructor(
                new MysterDataInputStream(new ByteArrayInputStream(bytes.toByteArray())),
                new MysterDataOutputStream(new ByteArrayOutputStream())));
        var stream=mock(MysterStream.class);
        when(stream.makeStreamConnection(any(ParamBuilder.class))).thenReturn(socket);
        var listener=mock(FileListerThread.ItemListListener.class);
        new FileListerThread(stream,Optional.of(key),listener,_ -> {},"127.0.0.1",type,_ -> "type").run();
        for (int i = 0; i < 3; i++) SwingUtilities.invokeAndWait(() -> {});
        verify(listener).finish();
        verify(listener,never()).failed(any());
        var params=ArgumentCaptor.forClass(ParamBuilder.class);
        verify(stream).makeStreamConnection(params.capture());
        assertEquals(Optional.of(key),params.getValue().getExpectedServerPublicKey());
        var failed=mock(FileListerThread.ItemListListener.class);
        when(stream.makeStreamConnection(any(ParamBuilder.class))).thenThrow(new IOException("wrong key"));
        new FileListerThread(stream,Optional.of(key),failed,_ -> {},"127.0.0.1",type,_ -> "type").run();
        for (int i = 0; i < 3; i++) SwingUtilities.invokeAndWait(() -> {});
        verify(failed).failed(any(IOException.class));
        verify(failed,never()).finish();
        verify(stream,never()).makeStreamConnection(any(MysterAddress.class));
    }

    @Test void reportsPercentageOnTheEdtBeforeTheListingFinishes() throws Exception {
        var type = new MysterType(new byte[16]);
        var key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic();
        var stream = mock(MysterStream.class);
        var listener = mock(FileListerThread.ItemListListener.class);
        var messages = mock(Sayable.class);
        var progressReceived = new CountDownLatch(1);
        doAnswer(invocation -> {
            assertTrue(SwingUtilities.isEventDispatchThread());
            if (invocation.getArgument(0).equals("Downloading file metadata: type 5%")) {
                progressReceived.countDown();
            }
            return null;
        }).when(messages).say(anyString());

        try (var input = new PipedInputStream();
             var output = new MysterDataOutputStream(new PipedOutputStream(input))) {
            var socket = mock(MysterSocket.class, withSettings().useConstructor(
                    new MysterDataInputStream(input),
                    new MysterDataOutputStream(new ByteArrayOutputStream())));
            when(stream.makeStreamConnection(any(ParamBuilder.class))).thenReturn(socket);
            var worker = new FileListerThread(stream, Optional.of(key), listener, messages,
                    "127.0.0.1", type, _ -> "type");
            worker.start();
            try {
                output.writeByte(1);
                output.writeInt(20);
                output.writeUTF("first");
                output.writeMessagePack(MessagePak.newEmpty());
                output.flush();

                assertTrue(progressReceived.await(5, TimeUnit.SECONDS),
                        "Progress must be delivered while the stream is waiting for more files");
                verify(listener, never()).finish();

                for (int i = 1; i < 20; i++) {
                    output.writeUTF("file" + i);
                    output.writeMessagePack(MessagePak.newEmpty());
                }
                output.flush();
                worker.join(5_000);
                assertFalse(worker.isAlive());
                SwingUtilities.invokeAndWait(() -> {});

                verify(messages).say("Downloading file metadata: type 50%");
                verify(messages).say("Downloading file metadata: type 100%");
                var ordered = inOrder(messages, listener);
                ordered.verify(messages).say("Downloading file metadata: type 100%");
                ordered.verify(messages).say("File List: type Complete.");
                ordered.verify(listener).finish();
                verify(listener, never()).failed(any());
            } finally {
                worker.flagToEnd();
                worker.join(5_000);
            }
        }
    }
}

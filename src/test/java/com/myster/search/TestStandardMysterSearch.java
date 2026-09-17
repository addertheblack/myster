package com.myster.search;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.io.OutputStream;
import java.net.InetAddress;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import com.general.thread.PromiseFutures;
import com.myster.mml.MessagePak;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.net.client.MysterProtocol;
import com.myster.net.stream.client.MysterDataInputStream;
import com.myster.net.stream.client.MysterDataOutputStream;
import com.myster.tracker.Tracker;
import com.myster.type.MysterType;
import com.myster.ui.MysterFrameContext;

class TestStandardMysterSearch {
    private final MysterType type = new MysterType(new byte[16]);
    private final SearchResultListener listener = mock(SearchResultListener.class);
    private final StandardMysterSearch search = new StandardMysterSearch(
            mock(MysterProtocol.class), mock(HashCrawlerManager.class), mock(MysterFrameContext.class),
            mock(Tracker.class), "test", type, listener);
    private final MysterDataInputStream input = mock(MysterDataInputStream.class);
    private final MysterSocket socket = mock(MysterSocket.class, withSettings().useConstructor(
            input, new MysterDataOutputStream(OutputStream.nullOutputStream())));

    @Test
    void postsMetadataToEdtInResultOrder() throws Exception {
        MysterSearchResult first = result("first");
        MysterSearchResult second = result("second");
        MessagePak firstStats = MessagePak.newEmpty();
        MessagePak secondStats = MessagePak.newEmpty();
        when(input.readByte()).thenReturn((byte) 1);
        when(input.readMessagePack()).thenReturn(firstStats, secondStats);
        doAnswer(_ -> {
            assertTrue(SwingUtilities.isEventDispatchThread());
            return null;
        }).when(listener).searchStats(any());

        PromiseFutures.execute(() -> {
            search.dealWithFileStats(socket, new MysterSearchResult[] { first, second });
            return null;
        }).get();
        SwingUtilities.invokeAndWait(() -> {});

        var order = inOrder(first, second, listener);
        order.verify(first).setFileStats(firstStats);
        order.verify(listener).searchStats(first);
        order.verify(second).setFileStats(secondStats);
        order.verify(listener).searchStats(second);
    }

    @Test
    void discardsQueuedMetadataAfterSearchIsStopped() throws Exception {
        MysterSearchResult result = result("cancelled");
        when(input.readByte()).thenReturn((byte) 1);
        when(input.readMessagePack()).thenReturn(MessagePak.newEmpty());

        // Keep the EDT occupied until cancellation so queued delivery cannot race this test.
        SwingUtilities.invokeAndWait(() -> {
            try {
                PromiseFutures.execute(() -> {
                    search.dealWithFileStats(socket, new MysterSearchResult[] { result });
                    return null;
                }).get();
                search.flagToEnd();
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        });
        SwingUtilities.invokeAndWait(() -> {});

        verifyNoInteractions(listener);
        org.mockito.Mockito.verify(result, org.mockito.Mockito.never()).setFileStats(any());
    }

    private MysterSearchResult result(String name) {
        MysterSearchResult result = mock(MysterSearchResult.class);
        when(result.getHostAddress()).thenReturn(new MysterAddress(InetAddress.getLoopbackAddress()));
        when(result.getName()).thenReturn(name);
        return result;
    }
}

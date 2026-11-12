package com.myster.search;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import com.general.thread.AsyncContext;
import com.general.thread.PromiseFuture;
import com.myster.net.MysterAddress;
import com.myster.net.client.MysterDatagram;
import com.myster.net.client.MysterProtocol;
import com.myster.tracker.MysterServer;
import com.myster.tracker.Tracker;
import com.myster.type.MysterType;
import com.myster.ui.MysterFrameContext;
import com.myster.util.Sayable;

class TestMysterSearch {
    private final MysterType type = new MysterType(new byte[16]);
    private final MysterProtocol protocol = mock(MysterProtocol.class);
    private final Tracker tracker = mock(Tracker.class);
    private final SearchResultListener listener = mock(SearchResultListener.class);
    private final Sayable progress = mock(Sayable.class);

    private MysterSearch search() {
        return new MysterSearch(protocol, null, mock(MysterFrameContext.class), tracker,
                listener, progress, type, "test");
    }

    @Test
    void cancellationSuppressesQueuedStartupProgressAndCompletionWithoutLaunchingNetwork() throws Exception {
        MysterSearch search = search();
        SwingUtilities.invokeAndWait(() -> {
            Thread producer = Thread.ofVirtual().start(search::run);
            try {
                producer.join();
            } catch (InterruptedException exception) {
                throw new AssertionError(exception);
            }
            search.flagToEnd();
            search.flagToEnd();
        });
        SwingUtilities.invokeAndWait(() -> {});
        assertTrue(search.isCancelled());
        assertTrue(search.isDone());
        verifyNoInteractions(listener, progress, protocol, tracker);
    }

    @Test
    void pendingUdpResultAndCompletionAreSuppressedWhenCancelledOnEdt() throws Exception {
        var datagram = mock(MysterDatagram.class);
        when(protocol.getDatagram()).thenReturn(datagram);
        var address = MysterAddress.createMysterAddress("192.0.2.1");
        var seed = mock(MysterServer.class);
        when(seed.getBestAddress()).thenReturn(java.util.Optional.of(address));
        // Five retained seeds avoid onramp DNS; the address queue still visits this endpoint once.
        when(tracker.getTop(type, 50)).thenReturn(new MysterServer[] {seed, seed, seed, seed, seed});
        AtomicReference<AsyncContext<List<String>>> result = new AtomicReference<>();
        PromiseFuture<List<String>> results = PromiseFuture.newPromiseFuture(result::set).useEdt();
        PromiseFuture<String[]> top = PromiseFuture.<String[]>newPromiseFuture(context -> {}).useEdt();
        when(datagram.getSearch(any(), eq(type), eq("test"))).thenReturn(results);
        when(datagram.getTopServers(any(), eq(type))).thenReturn(top);
        MysterSearch search = search();
        SwingUtilities.invokeAndWait(search::run);
        SwingUtilities.invokeAndWait(() -> {
            verify(listener).searchStart();
            clearInvocations(listener, progress);
            result.get().setResult(List.of("queued result"));
            search.flagToEnd();
        });
        SwingUtilities.invokeAndWait(() -> {});
        SwingUtilities.invokeAndWait(() -> {});
        assertTrue(search.isDone());
        assertTrue(top.isCancelled());
        verifyNoInteractions(listener, progress);
    }

    @Test
    void streamCallbacksCheckTheOwningSearchAtEdtDelivery() throws Exception {
        var searchRef = new AtomicReference<MysterSearch>();
        var streamListener = new AtomicReference<SearchResultListener>();
        SwingUtilities.invokeAndWait(() -> {
            try (var stream = mockConstruction(StandardMysterSearch.class, (_, construction) ->
                    streamListener.set((SearchResultListener) construction.arguments().get(6)))) {
                searchRef.set(search());
            }
            SearchResultListener producer = streamListener.get();
            Thread worker = Thread.ofVirtual().start(() -> {
                producer.searchStart();
                producer.addSearchResults(new SearchResult[] {mock(SearchResult.class)});
                producer.searchStats(mock(SearchResult.class));
                producer.searchOver();
            });
            try {
                worker.join();
            } catch (InterruptedException exception) {
                throw new AssertionError(exception);
            }
            searchRef.get().flagToEnd();
        });
        SwingUtilities.invokeAndWait(() -> {});
        verifyNoInteractions(listener);
    }

    @Test
    void successfulStartupAndCompletionAreDeliveredOnceOnEdt() throws Exception {
        // No enabled tracker list: bootstrap is stubbed to avoid DNS, with five unusable seeds.
        var seed = mock(MysterServer.class);
        when(tracker.getTop(type, 50)).thenReturn(new MysterServer[] {seed, seed, seed, seed, seed});
        List<String> events = new ArrayList<>();
        doAnswer(_ -> {
            assertTrue(SwingUtilities.isEventDispatchThread());
            events.add("start");
            return null;
        }).when(listener).searchStart();
        doAnswer(_ -> {
            assertTrue(SwingUtilities.isEventDispatchThread());
            events.add("over");
            return null;
        }).when(listener).searchOver();
        MysterSearch search = search();
        SwingUtilities.invokeAndWait(search::run);
        SwingUtilities.invokeAndWait(() -> {});
        assertEquals(List.of("start", "over"), events);
        search.flagToEnd();
        SwingUtilities.invokeAndWait(() -> {});
        assertEquals(List.of("start", "over"), events);
    }
}

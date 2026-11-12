package com.myster.search.ui;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import com.general.mclist.JMCList;
import com.general.thread.AsyncContext;
import com.general.thread.PromiseFuture;
import com.myster.net.MysterAddress;
import com.myster.net.client.MysterProtocol;
import com.myster.net.client.MysterStream;
import com.myster.search.SearchEngine;
import com.myster.search.SearchResultListener;
import com.myster.search.ServerSearch;
import com.myster.search.ServerSearchResult;
import com.myster.tracker.MysterAddressIdentity;
import com.myster.tracker.Tracker;
import com.myster.type.MetadataTypeId;
import com.myster.type.MysterType;
import com.myster.type.TypeDescription;
import com.myster.type.TypeDescriptionList;
import com.myster.type.TypeSource;
import com.myster.ui.MysterFrameContext;
import com.myster.util.Sayable;

class TestSearchTabServerMode {
    @Test
    @SuppressWarnings("unchecked")
    void modesKeepDisplayedRowsAndCancelStoppedReplacedAndDisposedSearches() throws Exception {
        MysterType type = new MysterType(new byte[16]);
        TypeDescription description = new TypeDescription(type, "pictures", "Pictures", new String[0],
                false, true, TypeSource.CUSTOM, false, MetadataTypeId.IMAGE);
        var types = mock(TypeDescriptionList.class);
        when(types.getEnabledTypes()).thenReturn(new TypeDescription[] {description});
        when(types.get(type)).thenReturn(Optional.of(description));
        var context = mock(MysterFrameContext.class);
        when(context.tdList()).thenReturn(types);
        var protocol = mock(MysterProtocol.class);
        var stream = mock(MysterStream.class);
        when(protocol.getStream()).thenReturn(stream);
        var address = MysterAddress.createMysterAddress("192.0.2.1");
        var result = new ServerSearchResult(new MysterAddressIdentity(address), address, "Server", "Desc", 0, 12);
        List<Consumer<List<ServerSearchResult>>> callbacks = new ArrayList<>();
        List<Sayable> progress = new ArrayList<>();
        List<AsyncContext<Void>> completions = new ArrayList<>();
        List<PromiseFuture<Void>> promises = new ArrayList<>();
        List<SearchResultListener> fileListeners = new ArrayList<>();

        SwingUtilities.invokeAndWait(() -> {
            try (var servers = mockConstruction(ServerSearch.class, (search, _) -> {
                when(search.search(any(), any(), any(), any())).thenAnswer(invocation -> {
                    callbacks.add(invocation.getArgument(2));
                    progress.add(invocation.getArgument(3));
                    PromiseFuture<Void> promise = PromiseFuture.newPromiseFuture(completions::add);
                    promises.add(promise);
                    return promise;
                });
            }); var files = mockConstruction(SearchEngine.class, (_, construction) -> {
                fileListeners.add((SearchResultListener) construction.arguments().get(4));
            })) {
                SearchTab tab = new SearchTab(context, protocol, null, mock(Tracker.class), _ -> {});
                assertEquals(SearchMode.FILES, tab.getSearchMode());
                tab.setSearchMode(SearchMode.SERVERS);
                tab.setSearchString("test");
                tab.startSearch();
                assertEquals("Stop", tab.getSearchButton().getText());
                assertTrue(tab.getTabTitle().startsWith("Servers: test"));
                callbacks.getFirst().accept(List.of(result));
                assertTrue(tab.getTabTitle().endsWith("(1)"));
                ServerSearchResults pane = field(tab, "serverResults", ServerSearchResults.class);
                assertEquals(1, pane.sizeOfResults());
                tab.setSearchMode(SearchMode.FILES);
                assertTrue(pane.isVisible());
                assertTrue(tab.getTabTitle().startsWith("Servers:"));
                tab.setThumbnailActive(true);
                tab.stopSearch();
                assertTrue(promises.getFirst().isCancelled());
                assertEquals(1, pane.sizeOfResults());
                assertEquals("Search", tab.getSearchButton().getText());

                tab.startSearch();
                fileListeners.getFirst().searchStart();
                assertFalse(pane.isVisible());
                assertEquals(0, pane.sizeOfResults());
                assertEquals("Stop", tab.getSearchButton().getText());
                assertEquals(0, pane.sizeOfResults());
                JMCList<?> fileList = field(tab, "fileList", JMCList.class);
                assertEquals("Resolution", fileList.getColumnName(2));

                tab.setSearchMode(SearchMode.SERVERS);
                tab.startSearch();
                verify(files.constructed().getFirst()).flagToEnd();
                assertEquals("Stop", tab.getSearchButton().getText());
                tab.dispose();
                completions.get(1).setResult(null);
                assertEquals(0, pane.sizeOfResults());
                assertTrue(promises.get(1).isCancelled());
                assertEquals("Search", tab.getSearchButton().getText());
            }
        });
        SwingUtilities.invokeAndWait(() -> {});
        verifyNoInteractions(stream);
    }

    @Test
    void modePreferencesAreStableAndRestorationDoesNotRunSearch() throws Exception {
        assertEquals(SearchMode.FILES, SearchMode.fromPreference(null));
        assertEquals(SearchMode.FILES, SearchMode.fromPreference("unknown"));
        for (SearchMode mode : SearchMode.values()) {
            assertEquals(mode, SearchMode.fromPreference(mode.preferenceValue()));
        }
        var type = new MysterType(new byte[16]);
        var description = new TypeDescription(type, "music", "Music", new String[0],
                false, true, TypeSource.DEFAULT, true, MetadataTypeId.AUDIO);
        var types = mock(TypeDescriptionList.class);
        when(types.getEnabledTypes()).thenReturn(new TypeDescription[] {description});
        when(types.get(type)).thenReturn(Optional.of(description));
        var context = mock(MysterFrameContext.class);
        when(context.tdList()).thenReturn(types);
        var protocol = mock(MysterProtocol.class);
        var stream = mock(MysterStream.class);
        when(protocol.getStream()).thenReturn(stream);
        SwingUtilities.invokeAndWait(() -> {
            var tab = new SearchTab(context, protocol, null, mock(Tracker.class), _ -> {});
            tab.setSearchMode(SearchMode.SERVERS);
            tab.restoreSearchState("");
            assertEquals("Servers: Music", tab.getTabTitle());
            assertTrue(field(tab, "serverResults", ServerSearchResults.class).isVisible());
            assertEquals("Search", tab.getSearchButton().getText());
            tab.dispose();
        });
        verifyNoInteractions(stream);
    }

    @Test
    void successfulCompletionKeepsFinalRowsAndReturnsToSearch() throws Exception {
        var type = new MysterType(new byte[16]);
        var description = new TypeDescription(type, "music", "Music", new String[0],
                false, true, TypeSource.DEFAULT, true, MetadataTypeId.AUDIO);
        var types = mock(TypeDescriptionList.class);
        when(types.getEnabledTypes()).thenReturn(new TypeDescription[] {description});
        when(types.get(type)).thenReturn(Optional.of(description));
        var context = mock(MysterFrameContext.class);
        when(context.tdList()).thenReturn(types);
        var protocol = mock(MysterProtocol.class);
        when(protocol.getStream()).thenReturn(mock(MysterStream.class));
        var address = MysterAddress.createMysterAddress("192.0.2.1");
        var result = new ServerSearchResult(new MysterAddressIdentity(address), address, "Name", "Desc", 0, -1);
        var tabRef = new java.util.concurrent.atomic.AtomicReference<SearchTab>();
        SwingUtilities.invokeAndWait(() -> {
            try (var servers = mockConstruction(ServerSearch.class, (search, _) -> {
                when(search.search(any(), any(), any(), any())).thenAnswer(invocation -> {
                    Consumer<List<ServerSearchResult>> callback = invocation.getArgument(2);
                    callback.accept(List.of(result));
                    return PromiseFuture.<Void>newPromiseFuture((Void) null);
                });
            })) {
                var tab = new SearchTab(context, protocol, null, mock(Tracker.class), _ -> {});
                tabRef.set(tab);
                tab.setSearchMode(SearchMode.SERVERS);
                tab.startSearch();
            }
        });
        SwingUtilities.invokeAndWait(() -> {
            SearchTab tab = tabRef.get();
            assertEquals("Search", tab.getSearchButton().getText());
            assertTrue(tab.getTabTitle().endsWith("(1)"));
            tab.stopSearch();
            assertEquals(1, field(tab, "serverResults", ServerSearchResults.class).sizeOfResults());
            tab.dispose();
        });
    }

    private static <T> T field(Object target, String name, Class<T> type) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return type.cast(field.get(target));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}

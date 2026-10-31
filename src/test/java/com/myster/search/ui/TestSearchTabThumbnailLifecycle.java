package com.myster.search.ui;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import com.general.mclist.JMCList;
import com.general.thread.PromiseFuture;
import com.myster.net.client.MysterDatagram;
import com.myster.net.client.MysterProtocol;
import com.myster.net.client.MysterStream;
import com.myster.search.SearchEngine;
import com.myster.search.SearchResult;
import com.myster.search.SearchResultListener;
import com.myster.tracker.Tracker;
import com.myster.type.MetadataTypeId;
import com.myster.type.MysterType;
import com.myster.type.TypeDescription;
import com.myster.type.TypeDescriptionList;
import com.myster.type.TypeSource;
import com.myster.ui.MysterFrameContext;

class TestSearchTabThumbnailLifecycle {
    @Test
    void selectorEditsAndStaleEngineCallbacksCannotReinterpretDisplayedGeneration() throws Exception {
        MysterType image = new MysterType(new byte[16]);
        byte[] other = new byte[16];
        other[15] = 1;
        MysterType audio = new MysterType(other);
        TypeDescription pictures = new TypeDescription(image, "pictures", "Pictures", new String[0],
                false, true, TypeSource.CUSTOM, false, MetadataTypeId.IMAGE);
        TypeDescription music = new TypeDescription(audio, "music", "Music", new String[0],
                false, true, TypeSource.DEFAULT, true, MetadataTypeId.AUDIO);
        TypeDescriptionList types = mock(TypeDescriptionList.class);
        when(types.getEnabledTypes()).thenReturn(new TypeDescription[] {pictures, music});
        when(types.get(image)).thenReturn(Optional.of(pictures));
        when(types.get(audio)).thenReturn(Optional.of(music));
        MysterFrameContext context = mock(MysterFrameContext.class);
        when(context.tdList()).thenReturn(types);
        MysterProtocol protocol = mock(MysterProtocol.class);
        MysterStream stream = mock(MysterStream.class);
        MysterDatagram datagram = mock(MysterDatagram.class);
        when(protocol.getStream()).thenReturn(stream);
        when(protocol.getDatagram()).thenReturn(datagram);
        when(datagram.ping(any())).thenReturn(PromiseFuture.newPromiseFuture(c -> {}));
        SearchResult result = mock(SearchResult.class);
        when(result.getName()).thenReturn("photo");
        when(result.getHostAddress()).thenReturn(new com.myster.net.MysterAddress(java.net.InetAddress.getLoopbackAddress()));
        when(result.getProtocol()).thenReturn(protocol);
        List<SearchResultListener> listeners = new ArrayList<>();
        List<MysterType> searchedTypes = new ArrayList<>();

        SwingUtilities.invokeAndWait(() -> {
            try (MockedConstruction<SearchEngine> engines = mockConstruction(SearchEngine.class, (_, construction) -> {
                listeners.add((SearchResultListener) construction.arguments().get(4));
                searchedTypes.add((MysterType) construction.arguments().get(6));
            })) {
                SearchTab tab = new SearchTab(context, protocol, null, mock(Tracker.class), _ -> {});
                tab.setMysterType(image);
                tab.startSearch();
                listeners.get(0).searchStart();
                tab.setMysterType(audio);
                listeners.get(0).addSearchResults(new SearchResult[] {result});
                JMCList<?> list = (JMCList<?>) java.util.Arrays.stream(tab.getComponents())
                        .filter(component -> component instanceof JScrollPane).map(component ->
                                ((JScrollPane) component).getViewport().getView()).findFirst().orElseThrow();
                assertEquals(1, list.getRowCount());
                assertEquals("Resolution", list.getColumnName(2));

                tab.startSearch();
                listeners.get(1).searchStart();
                assertEquals("Stop", tab.getSearchButton().getText());
                listeners.get(0).searchOver();
                listeners.get(0).searchStart();
                assertFalse(listeners.get(0).addSearchResults(new SearchResult[] {result}));
                assertEquals("Stop", tab.getSearchButton().getText());
                assertEquals(0, list.getRowCount());
                assertEquals(List.of(image, audio), searchedTypes);

                tab.stopSearch();
                listeners.get(1).searchOver();
                assertEquals("Search", tab.getSearchButton().getText());
                verify(engines.constructed().get(1)).flagToEnd();
                tab.dispose();
                tab.dispose();
                listeners.get(1).searchStart();
                assertEquals("Search", tab.getSearchButton().getText());
            }
        });
        TestSearchThumbnails.flush();
        verifyNoInteractions(stream);
        verify(types, times(1)).removeTypeListener(any());
    }
}

package com.myster.search.ui;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.awt.event.MouseEvent;
import java.security.KeyPairGenerator;
import java.util.List;
import java.util.Optional;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import com.general.mclist.JMCList;
import com.myster.client.ui.ClientWindowProvider;
import com.myster.net.MysterAddress;
import com.myster.search.ServerSearchResult;
import com.myster.tracker.BookmarkMysterServerList.Bookmark;
import com.myster.tracker.MysterAddressIdentity;
import com.myster.tracker.PublicKeyIdentity;
import com.myster.tracker.Tracker;
import com.myster.type.MysterType;
import com.myster.ui.MysterFrameContext;

class TestServerSearchResults {
    @Test
    void columnsRetainFullDescriptionAndCachedValuesAndActivationUsesCapturedType() throws Exception {
        var type = new MysterType(new byte[16]);
        var address = MysterAddress.createMysterAddress("192.0.2.1:7777");
        var identity = new PublicKeyIdentity(KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic());
        String description = "A long full description ".repeat(10);
        var result = new ServerSearchResult(identity, address, "Named", description, 0, 12);
        var context = mock(MysterFrameContext.class);
        var provider = mock(ClientWindowProvider.class);
        when(context.clientWindowProvider()).thenReturn(provider);
        var tracker = mock(Tracker.class);
        SwingUtilities.invokeAndWait(() -> {
            ServerSearchResults pane = new ServerSearchResults(context, tracker);
            pane.clear(type);
            JMCList<?> list = table(pane);
            doubleClick(list);
            verifyNoInteractions(provider);
            pane.addResults(List.of(result));
            assertEquals(5, list.getColumnCount());
            assertEquals(List.of("Server", "Description", "Address", "Files", "Ping"),
                    java.util.stream.IntStream.range(0, 5).mapToObj(list::getColumnName).toList());
            assertEquals(description, list.getValueAt(0, 1).toString());
            assertEquals("0", list.getValueAt(0, 3).toString());
            assertEquals("12ms", list.getValueAt(0, 4).toString());
            JComponent renderer = (JComponent) list.prepareRenderer(list.getCellRenderer(0, 1), 0, 1);
            assertEquals(description, renderer.getToolTipText());
            list.select(0);
            doubleClick(list);
            doubleClick(list);
            verify(provider, times(2)).openWindow(address, Optional.of(identity), Optional.of(type), Optional.empty());
            invokePrivate(pane, "bookmarkSelected");
            verify(tracker).addBookmark(new Bookmark(identity));
            pane.clear(type);
            doubleClick(list);
            verifyNoMoreInteractions(provider);
        });
    }

    @Test
    void addressOnlyIdentityAndDownPingUseExistingProviderPath() throws Exception {
        var type = new MysterType(new byte[16]);
        var address = MysterAddress.createMysterAddress("192.0.2.2");
        var context = mock(MysterFrameContext.class);
        var provider = mock(ClientWindowProvider.class);
        when(context.clientWindowProvider()).thenReturn(provider);
        SwingUtilities.invokeAndWait(() -> {
            var pane = new ServerSearchResults(context, mock(Tracker.class));
            pane.clear(type);
            pane.addResults(List.of(new ServerSearchResult(new MysterAddressIdentity(address), address,
                    "", "", 7, -2)));
            var list = table(pane);
            assertEquals(address.toString(), list.getValueAt(0, 0).toString());
            assertEquals("Down", list.getValueAt(0, 4).toString());
            list.select(0);
            doubleClick(list);
            verify(provider).openWindow(address, Optional.empty(), Optional.of(type), Optional.empty());
        });
    }

    private static JMCList<?> table(ServerSearchResults pane) {
        try {
            var field = ServerSearchResults.class.getDeclaredField("list");
            field.setAccessible(true);
            return (JMCList<?>) field.get(pane);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void doubleClick(JMCList<?> list) {
        var event = new MouseEvent(list, MouseEvent.MOUSE_CLICKED, 0, 0, 1, 1, 2, false);
        for (var listener : list.getMouseListeners()) {
            listener.mouseClicked(event);
        }
    }

    private static void invokePrivate(Object target, String name) {
        try {
            var method = target.getClass().getDeclaredMethod(name);
            method.setAccessible(true);
            method.invoke(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}

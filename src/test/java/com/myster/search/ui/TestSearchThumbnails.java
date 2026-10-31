package com.myster.search.ui;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.awt.GraphicsConfiguration;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.net.InetAddress;
import java.security.KeyPairGenerator;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.general.mclist.GenericMCListItem;
import com.general.mclist.IconTextCellRenderer;
import com.general.mclist.JMCList;
import com.general.mclist.Sortable;
import com.general.mclist.SortableString;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.net.client.MysterStream;
import com.myster.net.client.ParamBuilder;
import com.myster.search.SearchResult;
import com.myster.thumbnail.ui.ThumbnailIcon;
import com.myster.type.MetadataTypeId;
import com.myster.type.MysterType;

@Timeout(15)
class TestSearchThumbnails {
    static void flush() throws Exception {
        for (int i = 0; i < 8; i++) {
            SwingUtilities.invokeAndWait(() -> {});
        }
    }

    @Test
    void sameNamesAcrossServersLoadIndependentlyWithoutCachedServerObjects() throws Exception {
        Fixture f = new Fixture(2);
        try {
            f.show(MetadataTypeId.IMAGE);
            assertTrue(f.loaded.await(5, TimeUnit.SECONDS));
            flush();
            SwingUtilities.invokeAndWait(() -> {
                for (int row = 0; row < 2; row++) {
                    var component = (JLabel) f.list.prepareRenderer(f.list.getCellRenderer(row, 0), row, 0);
                    assertInstanceOf(ThumbnailIcon.class, component.getIcon());
                }
                f.list.reverseSortOrder();
            });
            flush();
            verify(f.results[0], never()).getServer();
            verify(f.results[1], never()).getServer();
            var params = org.mockito.ArgumentCaptor.forClass(ParamBuilder.class);
            verify(f.stream, times(2)).makeStreamConnection(params.capture());
            assertNotEquals(params.getAllValues().get(0).getAddress(), params.getAllValues().get(1).getAddress());
            assertTrue(params.getAllValues().stream().allMatch(p -> p.getExpectedServerPublicKey().isEmpty()));
        } finally {
            f.close();
        }
    }

    @Test
    void genericAudioAndUnknownProfilesStayTextOnly() throws Exception {
        Fixture f = new Fixture(1);
        try {
            SwingUtilities.invokeAndWait(() -> {
                for (MetadataTypeId profile : new MetadataTypeId[] {
                        MetadataTypeId.GENERIC, MetadataTypeId.AUDIO, MetadataTypeId.fromString("future") }) {
                    f.support.beginGeneration(f.type, profile);
                    f.support.setActive(true);
                    JLabel component = (JLabel) f.list.prepareRenderer(f.list.getCellRenderer(0, 0), 0, 0);
                    assertNull(component.getIcon());
                }
            });
            flush();
            verifyNoInteractions(f.stream);
        } finally {
            f.close();
        }
    }

    @Test
    void hiddenTabsDoNotLoadAndColumnRebuildingRestoresRenderer() throws Exception {
        Fixture f = new Fixture(1);
        try {
            SwingUtilities.invokeAndWait(() -> {
                f.support.beginGeneration(f.type, MetadataTypeId.VIDEO);
                f.list.setNumberOfColumns(2);
                f.list.setColumnWidth(0, 200);
            });
            flush();
            SwingUtilities.invokeAndWait(() ->
                    assertInstanceOf(IconTextCellRenderer.class, f.list.getCellRenderer(0, 0)));
            verifyNoInteractions(f.stream);
            SwingUtilities.invokeAndWait(() -> f.support.setActive(true));
            assertTrue(f.loaded.await(5, TimeUnit.SECONDS));
            flush();
        } finally {
            f.close();
        }
    }

    @Test
    void closingDuringTransferCannotRestoreOldPixels() throws Exception {
        Fixture f = new Fixture(1);
        CountDownLatch reading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            when(f.stream.getThumbnail(any(), any(), anyString(), anyInt())).thenAnswer(_ -> {
                reading.countDown();
                assertTrue(release.await(5, TimeUnit.SECONDS));
                return new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
            });
            f.show(MetadataTypeId.IMAGE);
            assertTrue(reading.await(5, TimeUnit.SECONDS));
            f.close();
            f.close();
            release.countDown();
            flush();
            verify(f.stream, times(1)).makeStreamConnection(any(ParamBuilder.class));
        } finally {
            release.countDown();
            f.close();
        }
    }

    private static final class Fixture implements AutoCloseable {
        final MysterStream stream = mock(MysterStream.class);
        final MysterType type = new MysterType(new byte[16]);
        final SearchResult[] results;
        final CountDownLatch loaded;
        JMCList<SearchResult> list;
        SearchThumbnailSupport support;

        Fixture(int count) throws Exception {
            loaded = new CountDownLatch(count);
            results = new SearchResult[count];
            GraphicsConfiguration configuration = mock(GraphicsConfiguration.class);
            when(configuration.getDefaultTransform()).thenReturn(AffineTransform.getScaleInstance(1.5, 1.5));
            var key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic();
            when(stream.makeStreamConnection(any(ParamBuilder.class))).thenAnswer(_ -> {
                MysterSocket socket = mock(MysterSocket.class);
                when(socket.getAuthenticatedPeerKey()).thenReturn(Optional.of(key));
                doAnswer(_ -> { loaded.countDown(); return null; }).when(socket).close();
                return socket;
            });
            when(stream.getThumbnail(any(), any(), anyString(), anyInt())).thenReturn(
                    new BufferedImage(30, 20, BufferedImage.TYPE_INT_ARGB));
            for (int i = 0; i < count; i++) {
                results[i] = mock(SearchResult.class);
                when(results[i].getHostAddress()).thenReturn(new MysterAddress(InetAddress.getLoopbackAddress(), 7000 + i));
                when(results[i].getName()).thenReturn("same/opaque.jpg");
            }
            SwingUtilities.invokeAndWait(() -> {
                list = new JMCList<>(1, true) {
                    private GraphicsConfiguration display;
                    { display = configuration; }
                    @Override public Rectangle getVisibleRect() { return new Rectangle(0, 0, 200, 100); }
                    @Override public boolean isShowing() { return true; }
                    @Override public GraphicsConfiguration getGraphicsConfiguration() { return display; }
                };
                list.setRowHeight(20);
                list.setSize(200, 100);
                list.setColumnWidth(0, 200);
                for (SearchResult result : results) {
                    list.addItem(new GenericMCListItem<>(new Sortable<?>[] {
                            new SortableString(result.getName()) }, result));
                }
                support = new SearchThumbnailSupport(list, stream);
            });
            flush();
        }

        void show(MetadataTypeId profile) throws Exception {
            SwingUtilities.invokeAndWait(() -> {
                support.beginGeneration(type, profile);
                support.setActive(true);
            });
        }

        @Override public void close() throws Exception {
            SwingUtilities.invokeAndWait(support::close);
        }
    }
}

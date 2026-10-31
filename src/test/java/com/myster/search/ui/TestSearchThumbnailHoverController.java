package com.myster.search.ui;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.net.InetAddress;
import java.security.KeyPairGenerator;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.general.mclist.GenericMCListItem;
import com.general.mclist.JMCList;
import com.general.mclist.Sortable;
import com.general.mclist.SortableString;
import com.general.thread.AsyncContext;
import com.general.thread.PromiseFuture;
import com.myster.net.MysterAddress;
import com.myster.search.SearchResult;
import com.myster.thumbnail.RemoteThumbnailCache;
import com.myster.type.MysterType;

@Timeout(10)
class TestSearchThumbnailHoverController {
    private static void flush() throws Exception {
        for (int i = 0; i < 5; i++) {
            SwingUtilities.invokeAndWait(() -> {});
        }
    }

    @Test
    void hoverOnAnyColumnUpgradesSmallImageWithoutSelectingRow() throws Exception {
        Fixture f = new Fixture();
        try {
            f.move(120, 5);
            verify(f.cache, never()).load(any());
            f.dwell();
            assertEquals(List.of(f.small), f.shown);
            assertEquals(-1, f.list.getSelectedIndex());
            SwingUtilities.invokeAndWait(() -> f.loading.setResult(f.large));
            flush();
            assertEquals(List.of(f.small, f.large), f.shown);
            assertEquals(f.positions.get(0), f.positions.get(1));
            assertEquals(-1, f.list.getSelectedIndex());
        } finally {
            f.close();
        }
    }

    @Test
    void exitAndSortingDismissAndStaleCompletionCannotReopen() throws Exception {
        Fixture f = new Fixture();
        try {
            f.move(20, 5);
            f.dwell();
            int shown = f.shown.size();
            AsyncContext<BufferedImage> old = f.loading;
            SwingUtilities.invokeAndWait(() -> f.list.sortBy(0));
            SwingUtilities.invokeAndWait(() -> old.setResult(f.large));
            flush();
            assertEquals(shown, f.shown.size());
            assertTrue(f.hides > 0);
            verify(f.cache, never()).close();
        } finally {
            f.close();
        }
    }

    @Test
    void rowChangeCancelsDwellAndInactiveHoverNeverLoads() throws Exception {
        Fixture f = new Fixture();
        try {
            f.move(20, 5);
            AsyncContext<Void> old = f.delay;
            f.move(20, 25);
            SwingUtilities.invokeAndWait(() -> old.setResult(null));
            flush();
            verify(f.cache, never()).load(any());
            f.dwell();
            verify(f.cache).load(argThat(request -> request.filename().equals("b")));
            SwingUtilities.invokeAndWait(() -> {
                f.active = false;
                f.controller.reset();
            });
            f.move(20, 5);
            f.dwell();
            verify(f.cache, times(1)).load(any());
        } finally {
            f.close();
        }
    }

    @Test
    void missingImagesDoNotCreateEmptyPopupAndCloseRestoresListeners() throws Exception {
        Fixture f = new Fixture();
        try {
            f.provideSmall = false;
            f.move(20, 5);
            f.dwell();
            assertTrue(f.shown.isEmpty());
            SwingUtilities.invokeAndWait(() -> f.loading.setResult(null));
            flush();
            assertTrue(f.shown.isEmpty());
            int listeners = f.list.getMouseMotionListeners().length;
            f.close();
            assertEquals(listeners - 1, f.list.getMouseMotionListeners().length);
            f.move(20, 25);
            f.dwell();
            verify(f.cache, times(1)).load(any());
        } finally {
            f.close();
        }
    }

    @Test
    void escapeWorksWithoutTableFocusAndRestoresWindowBinding() throws Exception {
        Fixture f = new Fixture();
        try {
            f.move(20, 5);
            f.dwell();
            int hides = f.hides;
            SwingUtilities.invokeAndWait(() -> {
                Object binding = f.list.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW)
                        .get(javax.swing.KeyStroke.getKeyStroke("ESCAPE"));
                assertNotNull(binding);
                f.list.getActionMap().get(binding).actionPerformed(null);
            });
            assertTrue(f.hides > hides);
            f.close();
            assertNull(f.list.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW)
                    .get(javax.swing.KeyStroke.getKeyStroke("ESCAPE")));
        } finally {
            f.close();
        }
    }

    @Test
    void wheelOverResultsScrollsParentAndDismissesPreview() throws Exception {
        Fixture f = new Fixture();
        try {
            SwingUtilities.invokeAndWait(() -> {
                for (int i = 0; i < 40; i++) {
                    SearchResult result = mock(SearchResult.class);
                    when(result.getName()).thenReturn("extra-" + i);
                    f.list.addItem(new GenericMCListItem<>(new Sortable<?>[] {
                            new SortableString(result.getName()), new SortableString("server") }, result));
                }
                f.list.getPane().setSize(200, 100);
                f.list.getPane().doLayout();
                ((javax.swing.JScrollPane) f.list.getPane()).getViewport().doLayout();
                // AWT forwards wheel events to ancestors only after lightweight peers exist.
                f.list.getPane().addNotify();
            });
            flush();
            f.move(20, 5);
            f.dwell();
            int hides = f.hides;
            SwingUtilities.invokeAndWait(() -> {
                var pane = (javax.swing.JScrollPane) f.list.getPane();
                assertEquals(0, pane.getVerticalScrollBar().getValue());
                f.list.dispatchEvent(new java.awt.event.MouseWheelEvent(f.list,
                        MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), 0, 20, 5, 20, 5, 0, false,
                        java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, 1));
                assertTrue(pane.getVerticalScrollBar().getValue() > 0,
                        "wheel input over a result must reach the enclosing scroll pane");
            });
            assertTrue(f.hides > hides, "scrolling also dismisses hover");
            f.close();
            SwingUtilities.invokeAndWait(() -> {
                int before = ((javax.swing.JScrollPane) f.list.getPane()).getVerticalScrollBar().getValue();
                f.list.dispatchEvent(new java.awt.event.MouseWheelEvent(f.list,
                        MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), 0, 20, 5, 20, 5, 0, false,
                        java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, 1));
                assertTrue(((javax.swing.JScrollPane) f.list.getPane()).getVerticalScrollBar().getValue() > before);
            });
        } finally {
            f.close();
            SwingUtilities.invokeAndWait(() -> f.list.getPane().removeNotify());
        }
    }

    @Test
    void popupPlacementRespectsNegativeMonitorCoordinates() {
        assertEquals(new Point(-200, 700), SearchThumbnailHoverController.clamp(
                new Point(-10, 890), new Dimension(200, 200), new Rectangle(-1200, 0, 1200, 900)));
    }

    private static final class Fixture implements AutoCloseable {
        final RemoteThumbnailCache cache = mock(RemoteThumbnailCache.class);
        final List<BufferedImage> shown = new ArrayList<>();
        final List<Point> positions = new ArrayList<>();
        final BufferedImage small = new BufferedImage(20, 10, BufferedImage.TYPE_INT_ARGB);
        final BufferedImage large = new BufferedImage(192, 96, BufferedImage.TYPE_INT_ARGB);
        AsyncContext<Void> delay;
        AsyncContext<BufferedImage> loading;
        SearchThumbnailHoverController controller;
        JMCList<SearchResult> list;
        int hides;
        boolean active = true;
        boolean provideSmall = true;

        Fixture() throws Exception {
            var key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic();
            var address = new MysterAddress(InetAddress.getLoopbackAddress());
            var type = new MysterType(new byte[16]);
            when(cache.lookup(any())).thenReturn(Optional.empty());
            when(cache.load(any())).thenAnswer(_ -> PromiseFuture.<BufferedImage>newPromiseFuture(c -> loading = c));
            SwingUtilities.invokeAndWait(() -> {
                list = new JMCList<>(2, true) {
                    @Override public Rectangle getVisibleRect() { return new Rectangle(0, 0, 200, 100); }
                };
                list.setRowHeight(20);
                list.setSize(200, 100);
                for (String name : List.of("a", "b")) {
                    SearchResult result = mock(SearchResult.class);
                    when(result.getName()).thenReturn(name);
                    list.addItem(new GenericMCListItem<>(new Sortable<?>[] {
                            new SortableString(name), new SortableString("server") }, result));
                }
                controller = new SearchThumbnailHoverController(list, cache,
                        (item, _, _, _) -> Optional.of(new RemoteThumbnailCache.Request(address, type,
                                item.getObject().getName(), 192, key)),
                        _ -> provideSmall ? Optional.of(small) : Optional.empty(), () -> active,
                        _ -> PromiseFuture.<Void>newPromiseFuture(c -> delay = c),
                        new SearchThumbnailHoverController.Presenter() {
                            @Override public void show(BufferedImage image, Point location) {
                                shown.add(image);
                                positions.add(new Point(location));
                            }
                            @Override public void hide() { hides++; }
                        });
            });
            flush();
        }

        void move(int x, int y) throws Exception {
            SwingUtilities.invokeAndWait(() -> {
                MouseEvent event = new MouseEvent(list, MouseEvent.MOUSE_MOVED, 0, 0, x, y, x, y, 0, false, MouseEvent.NOBUTTON);
                for (var listener : list.getMouseMotionListeners()) {
                    listener.mouseMoved(event);
                }
            });
        }

        void dwell() throws Exception {
            SwingUtilities.invokeAndWait(() -> {
                if (delay != null) {
                    delay.setResult(null);
                }
            });
            flush();
        }

        @Override public void close() throws Exception {
            SwingUtilities.invokeAndWait(controller::close);
        }
    }
}

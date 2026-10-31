package com.myster.thumbnail.ui;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.net.InetAddress;
import java.security.KeyPairGenerator;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.general.mclist.JMCList;
import com.general.mclist.SortableString;
import com.general.mclist.TreeMCListTableModel.TreeMCListItem;
import com.general.mclist.TreeMCListTableModel.TreePathString;
import com.general.thread.PromiseFuture;
import com.myster.net.MysterAddress;
import com.myster.thumbnail.RemoteThumbnailCache;
import com.myster.type.MysterType;

@Timeout(10)
class TestVisibleRowThumbnailController {
    @Test
    void visibleCompletedImageSurvivesSharedCacheEviction() throws Exception {
        RemoteThumbnailCache cache = mock(RemoteThumbnailCache.class);
        var image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        var key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic();
        var request = new RemoteThumbnailCache.Request(new MysterAddress(InetAddress.getLoopbackAddress()),
                new MysterType(new byte[16]), "photo", 20, key);
        when(cache.lookup(any())).thenReturn(Optional.empty());
        when(cache.load(any())).thenAnswer(_ -> PromiseFuture.newPromiseFuture(image).useEdt());
        CountDownLatch painted = new CountDownLatch(1);
        AtomicReference<VisibleRowThumbnailController<String>> controller = new AtomicReference<>();
        @SuppressWarnings("unchecked")
        TreeMCListItem<String> item = mock(TreeMCListItem.class);
        when(item.getParent()).thenReturn(new TreePathString(new String[] {}));
        doReturn(new SortableString("photo")).when(item).getValueOfColumn(0);

        SwingUtilities.invokeAndWait(() -> {
            JMCList<String> list = new JMCList<>(1, true) {
                @Override public Rectangle getVisibleRect() { return new Rectangle(0, 0, 200, 100); }
                @Override public void repaintItem(com.general.mclist.MCListItemInterface<?> row) {
                    painted.countDown();
                }
            };
            list.setSize(200, 100);
            list.setRowHeight(20);
            list.addItem(item);
            controller.set(new VisibleRowThumbnailController<>(list, cache,
                    (_, _, _, _) -> Optional.of(request), _ -> new Rectangle(0, 0, 20, 20), () -> true));
        });
        assertTrue(painted.await(5, TimeUnit.SECONDS));
        try {
            SwingUtilities.invokeAndWait(() -> assertTrue(controller.get().lookup(item, 20).isPresent(),
                    "visible successful pixels must survive shared-cache eviction without another scroll"));
        } finally {
            SwingUtilities.invokeAndWait(controller.get()::close);
        }
    }
    private static void flush() throws Exception {
        for (int i = 0; i < 6; i++) {
            SwingUtilities.invokeAndWait(() -> {});
        }
    }

    @Test
    void completionAdvancesWithoutScrollingAndMissesDoNotLoop() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.settle();
            assertEquals(java.util.List.of("a"), fixture.started);
            SwingUtilities.invokeAndWait(() -> fixture.pending.get("a").setResult(null));
            flush();
            assertEquals(java.util.List.of("a", "b"), fixture.started);
            SwingUtilities.invokeAndWait(() -> fixture.pending.get("b").setException(new java.io.IOException("offline")));
            flush();
            assertEquals(java.util.List.of("a", "b", "c"), fixture.started);
            SwingUtilities.invokeAndWait(() -> fixture.pending.get("c").setResult(
                    new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB)));
            flush();
            fixture.settle();
            assertEquals(java.util.List.of("a", "b", "c"), fixture.started);
            SwingUtilities.invokeAndWait(() -> {
                fixture.controller.close();
                fixture.controller.close();
                verify(fixture.cache, never()).close();
            });
        } finally {
            fixture.close();
        }
    }

    @Test
    void inactiveAndHorizontallyHiddenIconsDoNotCreateDemand() throws Exception {
        Fixture fixture = new Fixture();
        try {
            SwingUtilities.invokeAndWait(() -> {
                fixture.active = false;
                fixture.controller.reconcile();
            });
            fixture.settle();
            assertTrue(fixture.started.isEmpty());
            assertEquals(0, fixture.mappingCalls);
            SwingUtilities.invokeAndWait(() -> {
                fixture.active = true;
                fixture.area = new Rectangle(50, 0, 150, 100);
                fixture.controller.reconcile();
            });
            fixture.settle();
            assertTrue(fixture.started.isEmpty());
        } finally {
            fixture.close();
        }
    }

    @Test
    void cancellationAndSortingCannotRedirectCompletion() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.settle();
            SwingUtilities.invokeAndWait(() -> {
                fixture.list.sortBy(0);
                fixture.list.reverseSortOrder();
            });
            fixture.settle();
            SwingUtilities.invokeAndWait(() -> fixture.pending.get("a").setResult(
                    new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB)));
            flush();
            assertTrue(fixture.painted.contains("a"));
            int count = fixture.started.size();
            SwingUtilities.invokeAndWait(() -> {
                fixture.active = false;
                fixture.controller.reconcile();
            });
            flush();
            assertEquals(count, fixture.started.size());
            verify(fixture.cache, never()).close();
        } finally {
            fixture.close();
        }
    }

    private static final class Fixture implements AutoCloseable {
        final RemoteThumbnailCache cache = mock(RemoteThumbnailCache.class);
        final java.util.List<String> started = new java.util.ArrayList<>();
        final java.util.List<String> painted = new java.util.ArrayList<>();
        final java.util.Map<String, com.general.thread.AsyncContext<BufferedImage>> pending = new java.util.HashMap<>();
        com.general.thread.AsyncContext<Void> delay;
        VisibleRowThumbnailController<String> controller;
        JMCList<String> list;
        Rectangle area = new Rectangle(0, 0, 200, 100);
        boolean active;
        int mappingCalls;

        Fixture() throws Exception {
            var key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic();
            var address = new MysterAddress(InetAddress.getLoopbackAddress());
            var type = new MysterType(new byte[16]);
            when(cache.lookup(any())).thenReturn(Optional.empty());
            when(cache.load(any())).thenAnswer(call -> {
                RemoteThumbnailCache.Request request = call.getArgument(0);
                started.add(request.filename());
                return PromiseFuture.<BufferedImage>newPromiseFuture(context -> pending.put(request.filename(), context));
            });
            SwingUtilities.invokeAndWait(() -> {
                list = new JMCList<>(1, true) {
                    @Override public Rectangle getVisibleRect() { return new Rectangle(area); }
                    @Override public void repaintItem(com.general.mclist.MCListItemInterface<?> row) {
                        painted.add((String) row.getObject());
                    }
                };
                list.setSize(200, 100);
                list.setRowHeight(20);
                for (String name : java.util.List.of("a", "b", "c")) {
                    list.addItem(new com.general.mclist.GenericMCListItem<>(
                            new com.general.mclist.Sortable<?>[] { new SortableString(name) }, name));
                }
                controller = new VisibleRowThumbnailController<>(list, cache, (item, _, _, _) -> {
                    mappingCalls++;
                    return Optional.of(new RemoteThumbnailCache.Request(address, type, item.getObject(), 20, key));
                }, row -> new Rectangle(0, row * 20, 20, 20), () -> active,
                        _ -> PromiseFuture.<Void>newPromiseFuture(context -> delay = context));
                active = true;
            });
            flush();
            mappingCalls = 0;
        }

        void settle() throws Exception {
            SwingUtilities.invokeAndWait(controller::reconcile);
            flush();
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

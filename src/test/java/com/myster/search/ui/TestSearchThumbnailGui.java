package com.myster.search.ui;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.net.InetAddress;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JToolTip;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import com.general.mclist.GenericMCListItem;
import com.general.mclist.JMCList;
import com.general.mclist.Sortable;
import com.general.mclist.SortableString;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.net.client.MysterStream;
import com.myster.search.SearchResult;
import com.myster.type.MetadataTypeId;
import com.myster.type.MysterType;

/** Optional desktop smoke test: -Djava.awt.headless=false; skipped in headless suites. */
@EnabledIf("desktopAvailable")
class TestSearchThumbnailGui {
    static boolean desktopAvailable() {
        return !java.awt.GraphicsEnvironment.isHeadless();
    }
    @Test
    void actualHoverPopupAppearsAndDismissesWithoutSelection() throws Exception {
        // Initialize synthetic input before X11 can dispatch native crossing events under its lock.
        Class.forName("java.awt.event.MouseEvent");
        var key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic();
        var address = new MysterAddress(InetAddress.getLoopbackAddress());
        MysterStream stream = mock(MysterStream.class);
        MysterSocket socket = mock(MysterSocket.class);
        when(socket.getAuthenticatedPeerKey()).thenReturn(Optional.of(key));
        when(stream.makeStreamConnection(any(com.myster.net.client.ParamBuilder.class))).thenReturn(socket);
        BufferedImage image = new BufferedImage(256, 160, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        graphics.setColor(new Color(30, 110, 170));
        graphics.fillRect(0, 0, 256, 160);
        graphics.setColor(new Color(255, 205, 70));
        graphics.fillOval(145, 20, 65, 65);
        graphics.dispose();
        CountDownLatch previewRequested = new CountDownLatch(1);
        when(stream.getThumbnail(any(), any(), anyString(), anyInt())).thenAnswer(call -> {
            if ((int) call.getArgument(3) >= 192) {
                previewRequested.countDown();
            }
            return image;
        });
        AtomicReference<JFrame> frame = new AtomicReference<>();
        AtomicReference<JMCList<SearchResult>> list = new AtomicReference<>();
        AtomicReference<SearchThumbnailSupport> support = new AtomicReference<>();
        AtomicReference<Point> location = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                com.formdev.flatlaf.FlatLightLaf.setup();
                JFrame window = new JFrame("Search thumbnail smoke test");
                frame.set(window);
                JMCList<SearchResult> table = new JMCList<>(2, true);
                list.set(table);
                table.setColumnName(0, "File Name");
                table.setColumnName(1, "Server");
                table.setColumnWidth(0, 340);
                table.setColumnWidth(1, 180);
                table.setRowHeight(24);
                for (int i = 0; i < 6; i++) {
                    SearchResult result = mock(SearchResult.class);
                    when(result.getHostAddress()).thenReturn(address);
                    when(result.getName()).thenReturn("example-" + i + ".jpg");
                    table.addItem(new GenericMCListItem<>(new Sortable<?>[] {
                            new SortableString(result.getName()), new SortableString("Example peer") }, result));
                }
                SearchThumbnailSupport thumbnails = new SearchThumbnailSupport(table, stream);
                support.set(thumbnails);
                thumbnails.beginGeneration(new MysterType(new byte[16]), MetadataTypeId.IMAGE);
                window.add(table.getPane());
                window.setSize(600, 360);
                window.setLocation(160, 160);
                window.setVisible(true);
                thumbnails.setActive(true);
                Point point = new Point(120, 12);
                SwingUtilities.convertPointToScreen(point, table);
                location.set(point);
            });
            Robot robot = new Robot();
            robot.waitForIdle();
            SwingUtilities.invokeAndWait(() -> {
                frame.get().toFront();
                Point point = new Point(120, 12);
                SwingUtilities.convertPointToScreen(point, list.get());
                location.set(point);
            });
            robot.mouseMove(100, 100);
            robot.waitForIdle();
            robot.mouseMove(location.get().x, location.get().y);
            long hoverDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (previewRequested.getCount() != 0 && System.nanoTime() < hoverDeadline) {
                moveOverFirstRow(list.get());
                previewRequested.await(25, TimeUnit.MILLISECONDS);
            }
            boolean requested = previewRequested.getCount() == 0;
            if (!requested) {
                ImageIO.write(robot.createScreenCapture(frame.get().getBounds()), "png",
                        Path.of("/tmp/myster-search-thumbnails-failure.png").toFile());
            }
            assertTrue(requested, "hover request at " + location.get());
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            AtomicReference<JToolTip> tooltip = new AtomicReference<>();
            while (tooltip.get() == null && System.nanoTime() < deadline) {
                moveOverFirstRow(list.get());
                SwingUtilities.invokeAndWait(() -> {
                    for (Window window : Window.getWindows()) {
                        JToolTip found = findTooltip(window);
                        if (found != null) {
                            tooltip.set(found);
                        }
                    }
                });
                if (tooltip.get() == null) {
                    Thread.sleep(25);
                }
            }
            assertNotNull(tooltip.get(), "the real Swing popup must be showing");
            AtomicReference<Rectangle> bounds = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> {
                assertEquals(-1, list.get().getSelectedIndex());
                Rectangle rectangle = new Rectangle(frame.get().getBounds());
                rectangle.add(new Rectangle(tooltip.get().getLocationOnScreen(), tooltip.get().getSize()));
                bounds.set(rectangle);
            });
            robot.waitForIdle();
            java.awt.Toolkit.getDefaultToolkit().sync();
            long paintDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            Point center = new Point(tooltip.get().getLocationOnScreen());
            center.translate(96, 96);
            while (!robot.getPixelColor(center.x, center.y).equals(new Color(30, 110, 170))
                    && System.nanoTime() < paintDeadline) {
                robot.delay(25);
            }
            assertEquals(new Color(30, 110, 170), robot.getPixelColor(center.x, center.y),
                    "native tooltip must actually paint its image");
            ImageIO.write(robot.createScreenCapture(bounds.get()), "png", Path.of("/tmp/myster-search-thumbnails.png").toFile());
            robot.mouseMove(100, 100);
            robot.waitForIdle();
            TestSearchThumbnails.flush();
            SwingUtilities.invokeAndWait(() -> assertFalse(tooltip.get().isShowing()));
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (support.get() != null) {
                    support.get().close();
                }
                if (frame.get() != null) {
                    frame.get().dispose();
                }
            });
        }
    }

    private static void moveOverFirstRow(JMCList<?> list) throws Exception {
        SwingUtilities.invokeAndWait(() -> list.dispatchEvent(new java.awt.event.MouseEvent(list,
                java.awt.event.MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, 120, 12, 0, false)));
    }

    private static JToolTip findTooltip(Component component) {
        if (component instanceof JToolTip tooltip && tooltip.isShowing()) {
            return tooltip;
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                JToolTip found = findTooltip(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}

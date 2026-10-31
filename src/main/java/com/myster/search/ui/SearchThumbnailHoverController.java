package com.myster.search.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeListener;
import java.time.Duration;
import java.util.Optional;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JToolTip;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeListener;
import javax.swing.event.TableModelListener;

import com.general.mclist.JMCList;
import com.general.mclist.MCListItemInterface;
import com.general.thread.Invoker;
import com.general.thread.PromiseFuture;
import com.general.thread.PromiseFutures;
import com.myster.search.SearchResult;
import com.myster.thumbnail.RemoteThumbnailCache;
import com.myster.thumbnail.ui.ThumbnailIcon;
import com.myster.thumbnail.ui.VisibleRowThumbnailController.RequestFactory;

/**
 * One independently cancellable hover preview per tab. A 500 ms dwell on any cell requests at
 * most 192 logical units (subject to the protocol's device-pixel cap). Item identity and a token
 * guard every callback; hiding/scrolling/model changes never allow an old result to reopen a popup.
 */
final class SearchThumbnailHoverController implements AutoCloseable {
    private static final int SIDE = 192;
    private static final KeyStroke ESCAPE = KeyStroke.getKeyStroke("ESCAPE");

    interface Presenter {
        void show(BufferedImage image, Point location);
        void hide();
    }

    private final JMCList<SearchResult> list;
    private final RemoteThumbnailCache cache;
    private final RequestFactory<SearchResult> requests;
    private final Function<MCListItemInterface<SearchResult>, Optional<BufferedImage>> smallImage;
    private final BooleanSupplier active;
    private final Function<Duration, PromiseFuture<Void>> delay;
    private final Presenter presenter;
    private final Object escapeAction = new Object();
    private final Map<Integer, Object> previousEscape = new LinkedHashMap<>();

    private MCListItemInterface<SearchResult> item;
    private Point pointer;
    private Point popupLocation;
    private PromiseFuture<Void> dwell;
    private PromiseFuture<BufferedImage> loading;
    private Window window;
    private JViewport viewport;
    private long token;
    private boolean closed;

    private final MouseAdapter mouseListener = new MouseAdapter() {
        @Override public void mouseMoved(MouseEvent event) { moved(event.getPoint()); }
        @Override public void mouseDragged(MouseEvent event) { reset(); }
        @Override public void mouseExited(MouseEvent event) { reset(); }
        @Override public void mousePressed(MouseEvent event) { reset(); }
        @Override public void mouseWheelMoved(MouseWheelEvent event) { reset(); }
    };

    private final TableModelListener modelListener = _ -> reset();
    private final ChangeListener viewportListener = _ -> reset();
    private final PropertyChangeListener scaleListener = _ -> reset();

    private final WindowAdapter windowListener = new WindowAdapter() {
        @Override public void windowDeactivated(WindowEvent event) { reset(); }
        @Override public void windowIconified(WindowEvent event) { reset(); }
        @Override public void windowClosed(WindowEvent event) { reset(); }
    };

    private final HierarchyListener hierarchyListener = event -> {
        if ((event.getChangeFlags() & (HierarchyEvent.PARENT_CHANGED | HierarchyEvent.SHOWING_CHANGED)) != 0) {
            reset();
            attachOwner();
        }
    };

    SearchThumbnailHoverController(JMCList<SearchResult> list, RemoteThumbnailCache cache,
            RequestFactory<SearchResult> requests,
            Function<MCListItemInterface<SearchResult>, Optional<BufferedImage>> smallImage,
            BooleanSupplier active) {
        this(list, cache, requests, smallImage, active, PromiseFutures::delay, new ImagePopup(list));
    }

    // Injectable delay/presenter permit headless tests of the actual pointer and cancellation logic.
    SearchThumbnailHoverController(JMCList<SearchResult> list, RemoteThumbnailCache cache,
            RequestFactory<SearchResult> requests,
            Function<MCListItemInterface<SearchResult>, Optional<BufferedImage>> smallImage,
            BooleanSupplier active, Function<Duration, PromiseFuture<Void>> delay, Presenter presenter) {
        this.list = list;
        this.cache = cache;
        this.requests = requests;
        this.smallImage = smallImage;
        this.active = active;
        this.delay = delay;
        this.presenter = presenter;

        list.addMouseListener(mouseListener);
        list.addMouseMotionListener(mouseListener);
        // A wheel listener on the table prevents Swing from forwarding input to its scroll pane.
        list.getPane().addMouseWheelListener(mouseListener);
        list.getModel().addTableModelListener(modelListener);
        list.addHierarchyListener(hierarchyListener);
        list.addPropertyChangeListener("graphicsConfiguration", scaleListener);
        list.addPropertyChangeListener("rowHeight", scaleListener);

        for (int condition : new int[] { JComponent.WHEN_FOCUSED, JComponent.WHEN_IN_FOCUSED_WINDOW }) {
            previousEscape.put(condition, list.getInputMap(condition).get(ESCAPE));
            list.getInputMap(condition).put(ESCAPE, escapeAction);
        }

        list.getActionMap().put(escapeAction, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { reset(); }
        });

        attachOwner();
    }

    private void attachOwner() {
        if (window != null) {
            window.removeWindowListener(windowListener);
        }

        if (viewport != null) {
            viewport.removeChangeListener(viewportListener);
        }

        window = SwingUtilities.getWindowAncestor(list);
        viewport = list.getParent() instanceof JViewport found ? found : null;

        if (window != null) {
            window.addWindowListener(windowListener);
        }

        if (viewport != null) {
            viewport.addChangeListener(viewportListener);
        }
    }

    private MCListItemInterface<SearchResult> hit(Point point) {
        if (point == null || !list.getVisibleRect().contains(point)) {
            return null;
        }

        int row = list.rowAtPoint(point);

        if (row < 0 || list.columnAtPoint(point) < 0) {
            return null;
        }

        return list.getMCListItem(list.convertRowIndexToModel(row));
    }

    private void moved(Point point) {
        if (closed || !active.getAsBoolean()) {
            reset();
            return;
        }

        MCListItemInterface<SearchResult> next = hit(point);
        if (next == item && next != null) {
            pointer = point;
            return;
        }

        reset();

        if (next == null) {
            return;
        }

        item = next;
        pointer = point;
        long captured = token;

        PromiseFuture<Void> future = delay.apply(Duration.ofMillis(500)).withInvoker(Invoker.EDT);
        dwell = future;

        future.addResultListener(_ -> {
            if (valid(captured)) {
                dwell = null;
                load(captured);
            }
        });
    }

    private boolean valid(long captured) {
        return !closed && captured == token && active.getAsBoolean() && item != null && hit(pointer) == item;
    }

    private void load(long captured) {
        Optional<RemoteThumbnailCache.Request> request = requests.requestFor(item,
                                                                             SIDE,
                                                                             list.getGraphicsConfiguration(),
                                                                             true);

        if (request.isEmpty()) {
            return;
        }

        popupLocation = new Point(pointer.x + 16, pointer.y + 20);
        Optional<BufferedImage> cached = cache.lookup(request.get());
        cached.or(() -> smallImage.apply(item)).ifPresent(image -> presenter.show(image, popupLocation));
        if (cached.isPresent()) {
            return;
        }
        PromiseFuture<BufferedImage> future = cache.load(request.get()).withInvoker(Invoker.EDT);
        loading = future;
        future.addResultListener(image -> {
            if (image != null && loading == future && valid(captured)) {
                presenter.show(image, popupLocation);
            }
        }).addExceptionListener(_ -> {}).addFinallyListener(() -> {
            if (loading == future) {
                loading = null;
            }
        });
    }

    /** Dismisses current hover and makes every queued callback stale without affecting row loading. */
    void reset() {
        token++;
        item = null;
        pointer = null;
        PromiseFuture<Void> previousDwell = dwell;
        dwell = null;
        PromiseFuture<BufferedImage> previousLoading = loading;
        loading = null;

        if (previousDwell != null) {
            previousDwell.cancel();
        }

        if (previousLoading != null) {
            previousLoading.cancel();
        }

        presenter.hide();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;

        reset();

        list.removeMouseListener(mouseListener);
        list.removeMouseMotionListener(mouseListener);
        list.getPane().removeMouseWheelListener(mouseListener);
        list.getModel().removeTableModelListener(modelListener);
        list.removeHierarchyListener(hierarchyListener);
        list.removePropertyChangeListener("graphicsConfiguration", scaleListener);
        list.removePropertyChangeListener("rowHeight", scaleListener);

        if (window != null) {
            window.removeWindowListener(windowListener);
        }
        if (viewport != null) {
            viewport.removeChangeListener(viewportListener);
        }

        for (var binding : previousEscape.entrySet()) {
            if (list.getInputMap(binding.getKey()).get(ESCAPE) == escapeAction) {
                if (binding.getValue() == null) {
                    list.getInputMap(binding.getKey()).remove(ESCAPE);
                } else {
                    list.getInputMap(binding.getKey()).put(ESCAPE, binding.getValue());
                }
            }
        }

        list.getActionMap().remove(escapeAction);
    }

    static Point clamp(Point location, Dimension size, Rectangle usable) {
        return new Point(Math.max(usable.x, Math.min(location.x, usable.x + usable.width - size.width)),
                Math.max(usable.y, Math.min(location.y, usable.y + usable.height - size.height)));
    }

    private static final class ImagePopup implements Presenter {
        private final JMCList<?> owner;
        private final JToolTip tooltip = new JToolTip() {
            @Override
            public Dimension getPreferredSize() {
                Insets padding = getInsets();
                return new Dimension(SIDE + padding.left + padding.right,
                        SIDE + padding.top + padding.bottom);
            }
        };
        private final JLabel image = new JLabel();
        private Popup popup;

        ImagePopup(JMCList<?> owner) {
            this.owner = owner;
            tooltip.setComponent(owner);
            tooltip.setLayout(new BorderLayout());
            image.setPreferredSize(new Dimension(SIDE, SIDE));
            tooltip.add(image, BorderLayout.CENTER);
            tooltip.setFocusable(false);
        }

        @Override
        public void show(BufferedImage pixels, Point location) {
            image.setIcon(new ThumbnailIcon(pixels, SIDE));
            if (popup == null && owner.isShowing()) {
                Point screen = new Point(location);
                SwingUtilities.convertPointToScreen(screen, owner);
                GraphicsConfiguration configuration = owner.getGraphicsConfiguration();
                Rectangle usable = new Rectangle(configuration.getBounds());
                Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration);
                usable.x += insets.left;
                usable.y += insets.top;
                usable.width -= insets.left + insets.right;
                usable.height -= insets.top + insets.bottom;
                Dimension size = tooltip.getPreferredSize();
                screen = clamp(screen, size, usable);
                Point pointer = owner.getMousePosition();

                if (pointer != null) {
                    SwingUtilities.convertPointToScreen(pointer, owner);
                    if (new Rectangle(screen, size).contains(pointer)) {
                        screen.x = pointer.x - size.width - 16;
                        screen = clamp(screen, size, usable);
                    }
                }

                popup = PopupFactory.getSharedInstance().getPopup(owner, tooltip, screen.x, screen.y);
                popup.show();
            }
            image.repaint();
        }

        @Override
        public void hide() {
            if (popup != null) {
                popup.hide();
                popup = null;
            }
            image.setIcon(null);
        }
    }
}

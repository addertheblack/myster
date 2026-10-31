package com.myster.thumbnail.ui;

import java.awt.GraphicsConfiguration;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeListener;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.IntFunction;
import java.util.function.Function;

import javax.swing.Icon;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;

import com.general.mclist.JMCList;
import com.general.mclist.MCListItemInterface;
import com.general.thread.Invoker;
import com.general.thread.PromiseFuture;
import com.general.thread.PromiseFutures;
import com.myster.thumbnail.RemoteThumbnailCache;

/**
 * EDT-owned visible-item demand shared by flat and tree lists. Geometry and request mapping are
 * supplied by the owner; rendering only reads {@link #lookup}. This consumer borrows its cache:
 * close withdraws only its own work. Completed pixels stay referenced until their demand leaves
 * the viewport, even if another consumer evicts them from the shared cache.
 */
public final class VisibleRowThumbnailController<E> implements AutoCloseable {
    /** Passive calls pass false; only demand reconciliation may initiate metadata resolution. */
    @FunctionalInterface
    public interface RequestFactory<E> {
        Optional<RemoteThumbnailCache.Request> requestFor(MCListItemInterface<E> item, int logicalSize,
                GraphicsConfiguration configuration, boolean allowResolution);
    }

    private record Visible<E>(MCListItemInterface<E> item, RemoteThumbnailCache.Request request) {}

    private final JMCList<E> list;
    private final RemoteThumbnailCache cache;
    private final RequestFactory<E> requests;
    private final IntFunction<Rectangle> iconBounds;
    private final BooleanSupplier active;
    private final Function<Duration, PromiseFuture<Void>> delay;
    private final List<Visible<E>> visible = new ArrayList<>();
    private final Map<RemoteThumbnailCache.Request, Optional<BufferedImage>> outcomes = new LinkedHashMap<>();
    private PromiseFuture<Void> settle;
    private PromiseFuture<BufferedImage> current;
    private RemoteThumbnailCache.Request currentRequest;
    private boolean closed;
    private boolean reconcileQueued;
    private TableModel model;
    private TableColumnModel columns;
    private JViewport viewport;

    private final TableModelListener modelListener = _ -> scheduleReconcile();
    private final ChangeListener viewportListener = _ -> reconcile();
    private final HierarchyListener hierarchyListener = event -> {
        if ((event.getChangeFlags() & (HierarchyEvent.SHOWING_CHANGED | HierarchyEvent.PARENT_CHANGED)) != 0) {
            attachViewport();
            reconcile();
        }
    };
    private final PropertyChangeListener propertyListener = event -> {
        if ("model".equals(event.getPropertyName()) || "columnModel".equals(event.getPropertyName())) {
            attachModels();
        }
        scheduleReconcile();
    };
    private final ComponentAdapter componentListener = new ComponentAdapter() {
        @Override public void componentResized(ComponentEvent event) { reconcile(); }
        @Override public void componentShown(ComponentEvent event) { reconcile(); }
        @Override public void componentHidden(ComponentEvent event) { reconcile(); }
    };
    private final TableColumnModelListener columnListener = new TableColumnModelListener() {
        @Override public void columnAdded(TableColumnModelEvent event) { scheduleReconcile(); }
        @Override public void columnRemoved(TableColumnModelEvent event) { scheduleReconcile(); }
        @Override public void columnMoved(TableColumnModelEvent event) { scheduleReconcile(); }
        @Override public void columnMarginChanged(ChangeEvent event) { reconcile(); }
        @Override public void columnSelectionChanged(ListSelectionEvent event) {}
    };

    public VisibleRowThumbnailController(JMCList<E> list,
                                         RemoteThumbnailCache cache,
                                         RequestFactory<E> requests,
                                         IntFunction<Rectangle> iconBounds,
                                         BooleanSupplier active) {
        this(list, cache, requests, iconBounds, active, PromiseFutures::delay);
    }

    // Delay injection keeps viewport-settling tests deterministic.
    VisibleRowThumbnailController(JMCList<E> list, RemoteThumbnailCache cache,
            RequestFactory<E> requests, IntFunction<Rectangle> iconBounds, BooleanSupplier active,
            Function<Duration, PromiseFuture<Void>> delay) {
        this.list = list;
        this.cache = cache;
        this.requests = requests;
        this.iconBounds = iconBounds;
        this.active = active;
        this.delay = delay;
        attachModels();
        attachViewport();
        list.addComponentListener(componentListener);
        list.addHierarchyListener(hierarchyListener);
        for (String name : List.of("graphicsConfiguration", "rowHeight", "font", "model", "columnModel")) {
            list.addPropertyChangeListener(name, propertyListener);
        }
        reconcile();
    }

    /** Returns pixels already available, without starting work, metadata lookup or repainting. */
    public Optional<Icon> lookup(MCListItemInterface<E> item, int logicalSize) {
        return lookupImage(item, logicalSize).map(pixels -> new ThumbnailIcon(pixels, logicalSize));
    }

    /** Passive pixel lookup for a second consumer such as a hover preview. */
    public Optional<BufferedImage> lookupImage(MCListItemInterface<E> item, int logicalSize) {
        if (closed || item == null) {
            return Optional.empty();
        }
        RemoteThumbnailCache.Request request = visible.stream().filter(entry -> entry.item == item)
                .map(Visible::request).findFirst().orElseGet(() ->
                        requests.requestFor(item, logicalSize, list.getGraphicsConfiguration(), false).orElse(null));
        if (request == null) {
            return Optional.empty();
        }
        Optional<BufferedImage> image = outcomes.get(request);
        return image != null ? image : cache.lookup(request);
    }

    /** Drops this consumer's old-generation state without resetting or closing its borrowed cache. */
    public void reset() {
        cancelSettle();
        cancelCurrent();
        visible.clear();
        outcomes.clear();
    }

    /** Immediately withdraws departed demand and settles new work for 75 ms. */
    public void reconcile() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::reconcile);
            return;
        }
        if (closed) {
            return;
        }
        cancelSettle();
        visible.clear();
        if (active.getAsBoolean()) {
            collectVisible();
        }
        outcomes.keySet().removeIf(request -> !contains(request));
        if (currentRequest != null && !contains(currentRequest)) {
            cancelCurrent();
        }
        if (visible.isEmpty()) {
            return;
        }
        PromiseFuture<Void> next = delay.apply(Duration.ofMillis(75)).withInvoker(Invoker.EDT);
        settle = next;
        next.addResultListener(_ -> {
            if (settle == next && !closed) {
                settle = null;
                startNext();
            }
        });
    }

    private void collectVisible() {
        Rectangle area = list.getVisibleRect();
        if (area.isEmpty() || list.getRowCount() == 0) {
            return;
        }
        int first = Math.max(0, list.rowAtPoint(area.getLocation()));
        int last = list.rowAtPoint(new java.awt.Point(area.x, area.y + area.height - 1));
        if (last < 0) {
            last = list.getRowCount() - 1;
        }
        for (int row = first; row <= last && row < list.getRowCount(); row++) {
            Rectangle bounds = iconBounds.apply(row);
            if (!area.intersects(bounds)) {
                continue;
            }
            MCListItemInterface<E> item = list.getMCListItem(list.convertRowIndexToModel(row));
            requests.requestFor(item, list.getRowHeight(row), list.getGraphicsConfiguration(), true)
                    .ifPresent(request -> visible.add(new Visible<>(item, request)));
        }
    }

    private boolean contains(RemoteThumbnailCache.Request request) {
        return visible.stream().anyMatch(entry -> entry.request.equals(request));
    }

    private void startNext() {
        if (closed || current != null || settle != null || !active.getAsBoolean()) {
            return;
        }
        for (Visible<E> entry : visible) {
            RemoteThumbnailCache.Request request = entry.request;
            if (outcomes.containsKey(request)) {
                continue;
            }
            Optional<BufferedImage> cached = cache.lookup(request);
            if (cached.isPresent()) {
                outcomes.put(request, cached);
                repaint(request);
                continue;
            }
            PromiseFuture<BufferedImage> future = cache.load(request).withInvoker(Invoker.EDT);
            current = future;
            currentRequest = request;
            future.addFinallyCallResultListener(result -> {
                if (current != future || closed) {
                    return;
                }
                current = null;
                currentRequest = null;
                if (contains(request) && !result.isCancelled()) {
                    outcomes.put(request, result.isResult() ? Optional.ofNullable(result.getResult()) : Optional.empty());
                    repaint(request);
                }
                SwingUtilities.invokeLater(this::startNext);
            });
            return;
        }
    }

    private void repaint(RemoteThumbnailCache.Request request) {
        for (Visible<E> entry : visible) {
            if (entry.request.equals(request)) {
                list.repaintItem(entry.item);
            }
        }
    }

    private void scheduleReconcile() {
        if (!reconcileQueued && !closed) {
            reconcileQueued = true;
            SwingUtilities.invokeLater(() -> {
                reconcileQueued = false;
                reconcile();
            });
        }
    }

    private void attachModels() {
        if (model != null) {
            model.removeTableModelListener(modelListener);
        }
        if (columns != null) {
            columns.removeColumnModelListener(columnListener);
        }
        model = list.getModel();
        columns = list.getColumnModel();
        model.addTableModelListener(modelListener);
        columns.addColumnModelListener(columnListener);
    }

    private void attachViewport() {
        if (viewport != null) {
            viewport.removeChangeListener(viewportListener);
        }
        viewport = list.getParent() instanceof JViewport found ? found : null;
        if (viewport != null) {
            viewport.addChangeListener(viewportListener);
        }
    }

    private void cancelSettle() {
        PromiseFuture<Void> previous = settle;
        settle = null;
        if (previous != null) {
            previous.cancel();
        }
    }

    private void cancelCurrent() {
        PromiseFuture<BufferedImage> previous = current;
        current = null;
        currentRequest = null;
        if (previous != null) {
            previous.cancel();
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        cancelSettle();
        cancelCurrent();
        model.removeTableModelListener(modelListener);
        columns.removeColumnModelListener(columnListener);
        list.removeComponentListener(componentListener);
        list.removeHierarchyListener(hierarchyListener);
        for (String name : List.of("graphicsConfiguration", "rowHeight", "font", "model", "columnModel")) {
            list.removePropertyChangeListener(name, propertyListener);
        }
        if (viewport != null) {
            viewport.removeChangeListener(viewportListener);
        }
        visible.clear();
        outcomes.clear();
    }
}

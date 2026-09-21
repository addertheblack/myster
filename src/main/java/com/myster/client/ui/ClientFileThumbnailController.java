package com.myster.client.ui;

import java.awt.GraphicsConfiguration;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.HierarchyEvent;
import java.beans.PropertyChangeListener;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import javax.swing.Icon;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

import com.general.mclist.JMCList;
import com.general.mclist.TreeMCList;
import com.general.mclist.TreeMCListTableModel.TreeMCListItem;
import com.general.thread.PromiseFuture;
import com.general.thread.PromiseFutures;
import com.myster.thumbnail.RemoteThumbnailCache;
import com.myster.thumbnail.ui.ThumbnailIcon;
import com.myster.thumbnail.ui.ThumbnailUiUtils;

/**
 * EDT-owned visible-row thumbnail demand. Rendering only calls {@link #lookup}; acquisition is
 * driven by viewport/model changes and is limited to one list promise at a time.
 */
public final class ClientFileThumbnailController<E> implements AutoCloseable {
    /**
     * Builds a request for an item. The boolean permits asynchronous metadata resolution; passive
     * provider lookup passes false and must never initiate work.
     */
    @FunctionalInterface
    public interface RequestFactory<E> {
        Optional<RemoteThumbnailCache.Request> requestFor(TreeMCListItem<E> item,
                                                           int logicalSize,
                                                           GraphicsConfiguration configuration,
                                                           boolean allowResolution);
    }

    private final JMCList<E> list;
    private final RemoteThumbnailCache cache;
    private final RequestFactory<E> requestFactory;
    private final BooleanSupplier active;
    /**
     * The tree items currently visible, in row order. TreeMCListItem uses Object identity
     * equality, so LinkedHashMap preserves both item identity and deterministic load order.
     */
    private final Map<TreeMCListItem<E>, RemoteThumbnailCache.Request> visible = new LinkedHashMap<>();
    private final Map<RemoteThumbnailCache.Request, Boolean> outcomes = new LinkedHashMap<>();
    private PromiseFuture<Void> settleDelay;
    private PromiseFuture<BufferedImage> currentFuture;
    private RemoteThumbnailCache.Request currentRequest;
    private boolean closed;

    private final TableModelListener modelListener = this::modelChanged;
    private final ComponentAdapter componentListener = new ComponentAdapter() {
        @Override public void componentResized(ComponentEvent event) { reconcile(); }
        @Override public void componentShown(ComponentEvent event) { reconcile(); }
    };
    private final ChangeListener viewportListener = _ -> reconcile();
    private final PropertyChangeListener propertyListener = _ -> reconcile();
    private JViewport viewport;

    public ClientFileThumbnailController(JMCList<E> list,
                                         RemoteThumbnailCache cache,
                                         RequestFactory<E> requestFactory,
                                         BooleanSupplier active) {
        this.list = list;
        this.cache = cache;
        this.requestFactory = requestFactory;
        this.active = active;
        list.getModel().addTableModelListener(modelListener);
        list.addComponentListener(componentListener);
        list.addHierarchyListener(this::hierarchyChanged);
        list.addPropertyChangeListener("graphicsConfiguration", propertyListener);
        list.addPropertyChangeListener("rowHeight", propertyListener);
        list.addPropertyChangeListener("font", propertyListener);
        if (list.getParent() instanceof JViewport foundViewport) {
            viewport = foundViewport;
            viewport.addChangeListener(viewportListener);
        }
        reconcile();
    }

    /** Returns a completed cached icon without initiating loading, repainting, or metadata work. */
    public Optional<Icon> lookup(TreeMCListItem<E> item, int logicalSize) {
        if (closed || item == null || item.isContainer()) {
            return Optional.empty();
        }
        RemoteThumbnailCache.Request request = visible.get(item);
        if (request == null) {
            request = requestFactory.requestFor(item, logicalSize, list.getGraphicsConfiguration(), false)
                    .orElse(null);
        }
        if (request == null || outcomes.containsKey(request) && !Boolean.TRUE.equals(outcomes.get(request))) {
            return Optional.empty();
        }
        return cache.lookup(request).map(image -> new ThumbnailIcon(image, logicalSize));
    }

    /** Reconciles currently visible eligible rows on the EDT. */
    public void reconcile() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::reconcile);
            return;
        }
        if (closed) return;
        cancelSettle();
        Map<TreeMCListItem<E>, RemoteThumbnailCache.Request> next = visibleRequests();
        visible.clear();
        visible.putAll(next);
        outcomes.keySet().removeIf(request -> !visible.containsValue(request));
        if (!active.getAsBoolean()) {
            cancelCurrent();
            return;
        }
        if (currentRequest != null && !visible.containsValue(currentRequest)) {
            cancelCurrent();
        }
        settleDelay = PromiseFutures.delay(Duration.ofMillis(75)).useEdt()
                .addResultListener(_ -> {
                    settleDelay = null;
                    startNext();
                });
    }

    private Map<TreeMCListItem<E>, RemoteThumbnailCache.Request> visibleRequests() {
        Map<TreeMCListItem<E>, RemoteThumbnailCache.Request> result = new LinkedHashMap<>();
        Rectangle viewport = list.getVisibleRect();
        if (viewport.width <= 0 || viewport.height <= 0 || list.getRowCount() == 0) return result;
        int first = Math.max(0, list.rowAtPoint(viewport.getLocation()));
        int lastPoint = Math.max(viewport.y, viewport.y + viewport.height - 1);
        int last = list.rowAtPoint(new java.awt.Point(viewport.x, lastPoint));
        if (last < 0) last = list.getRowCount() - 1;
        last = Math.min(last, list.getRowCount() - 1);
        int logicalSize = Math.max(1, list.getRowHeight());
        for (int row = first; row <= last; row++) {
            Rectangle iconBounds = TreeMCList.getItemIconBounds(list, row);
            Rectangle cell = list.getCellRect(row, 0, false);
            if (!viewport.intersects(iconBounds) || !cell.intersects(viewport)) continue;
            @SuppressWarnings("unchecked")
            TreeMCListItem<E> item = (TreeMCListItem<E>) list.getMCListItem(row);
            if (item.isContainer()) continue;
            Optional<RemoteThumbnailCache.Request> request = requestFactory.requestFor(
                    item, logicalSize, list.getGraphicsConfiguration(), true);
            request.ifPresent(value -> result.put(item, value));
        }
        return result;
    }

    private void startNext() {
        if (closed || currentFuture != null || !active.getAsBoolean()) return;
        for (Map.Entry<TreeMCListItem<E>, RemoteThumbnailCache.Request> entry : visible.entrySet()) {
            RemoteThumbnailCache.Request request = entry.getValue();
            if (outcomes.containsKey(request)) continue;
            Optional<BufferedImage> cached = cache.lookup(request);
            if (cached.isPresent()) {
                outcomes.put(request, true);
                continue;
            }
            currentRequest = request;
            PromiseFuture<BufferedImage> future = cache.load(request);
            currentFuture = future;
            future.addResultListener(image -> {
                if (currentRequest != request || !visible.containsValue(request)) return;
                outcomes.put(request, image != null);
                if (image != null) {
                    for (TreeMCListItem<E> item : matchingItems(request)) list.repaintItem(item);
                }
            }).addExceptionListener(_ -> {
                if (currentRequest == request && visible.containsValue(request)) outcomes.put(request, false);
            }).addCancelListener(() -> {}).addFinallyListener(() -> {
                if (currentFuture == future) {
                    currentFuture = null;
                    currentRequest = null;
                    startNext();
                }
            });
            return;
        }
    }

    private List<TreeMCListItem<E>> matchingItems(RemoteThumbnailCache.Request request) {
        List<TreeMCListItem<E>> result = new ArrayList<>();
        visible.forEach((item, value) -> { if (value.equals(request)) result.add(item); });
        return result;
    }

    private void modelChanged(TableModelEvent event) {
        SwingUtilities.invokeLater(this::reconcile);
    }

    private void hierarchyChanged(HierarchyEvent event) {
        if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) reconcile();
    }

    private void cancelSettle() {
        if (settleDelay != null) {
            settleDelay.cancel();
            settleDelay = null;
        }
    }

    private void cancelCurrent() {
        if (currentFuture != null) currentFuture.cancel();
        currentFuture = null;
        currentRequest = null;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        cancelSettle();
        cancelCurrent();
        list.getModel().removeTableModelListener(modelListener);
        list.removeComponentListener(componentListener);
        list.removePropertyChangeListener("graphicsConfiguration", propertyListener);
        list.removePropertyChangeListener("rowHeight", propertyListener);
        list.removePropertyChangeListener("font", propertyListener);
        if (viewport != null) {
            viewport.removeChangeListener(viewportListener);
        }
        visible.clear();
        outcomes.clear();
        cache.close();
    }
}

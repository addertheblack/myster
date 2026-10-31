package com.myster.search.ui;

import java.awt.GraphicsConfiguration;
import java.awt.image.BufferedImage;
import java.security.PublicKey;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

import javax.swing.Icon;
import javax.swing.SwingUtilities;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.general.mclist.IconTextCellRenderer;
import com.general.mclist.JMCList;
import com.general.mclist.MCListItemInterface;
import com.general.util.IconLoader;
import com.myster.net.client.MysterStream;
import com.myster.search.SearchResult;
import com.myster.thumbnail.RemoteThumbnailCache;
import com.myster.thumbnail.ui.ThumbnailUiUtils;
import com.myster.thumbnail.ui.VisibleRowThumbnailController;
import com.myster.type.MetadataTypeId;
import com.myster.type.MysterType;

/**
 * Per-tab thumbnail composition. Owns cache, row and hover consumers; the list widget remains
 * unaware of loading and icon drawing. Type/profile and each item's first resolved key belong
 * to the displayed generation, independently of subsequent edits to the type selector.
 */
final class SearchThumbnailSupport implements AutoCloseable {
    private final JMCList<SearchResult> list;
    private final RemoteThumbnailCache cache;
    private final VisibleRowThumbnailController<SearchResult> rows;
    private final SearchThumbnailHoverController hover;
    private final IconTextCellRenderer<MCListItemInterface<SearchResult>> renderer;
    private final Map<MCListItemInterface<SearchResult>, PublicKey> keys = new IdentityHashMap<>();
    private final FlatSVGIcon fallback = IconLoader.loadSvg(IconLoader.class, "file-svgrepo-com");
    private final TableModelListener structureListener = event -> {
        if (event.getFirstRow() == TableModelEvent.HEADER_ROW) {
            SwingUtilities.invokeLater(this::columnsRebuilt);
        }
    };

    private MysterType type;
    private MetadataTypeId profile = MetadataTypeId.GENERIC;
    private boolean active;
    private boolean closed;

    SearchThumbnailSupport(JMCList<SearchResult> list, MysterStream stream) {
        this.list = list;
        cache = new RemoteThumbnailCache(stream);
        fallback.setColorFilter(new FlatSVGIcon.ColorFilter((component, _) -> component.getForeground()));
        renderer = new IconTextCellRenderer<>(
                (table, row) -> list.getMCListItem(table.convertRowIndexToModel(row)), this::iconFor);
        rows = new VisibleRowThumbnailController<>(list, cache, this::requestFor,
                row -> renderer.getIconBounds(list, row, 0), this::isActive);
        hover = new SearchThumbnailHoverController(list, cache, this::requestFor,
                this::smallImage, this::isActive);
        list.getModel().addTableModelListener(structureListener);
        columnsRebuilt();
    }

    void beginGeneration(MysterType type, MetadataTypeId profile) {
        hover.reset();
        this.profile = MetadataTypeId.GENERIC;
        rows.reset();
        cache.reset();
        keys.clear();
        this.type = type;
        this.profile = profile;
        rows.reconcile();
    }

    void setActive(boolean active) {
        this.active = active;
        hover.reset();
        rows.reconcile();
    }

    private boolean isActive() {
        return active && !closed && list.isShowing();
    }

    void resultsChanged() {
        rows.reconcile();
    }

    void columnsRebuilt() {
        if (closed) {
            return;
        }
        int column = list.convertColumnIndexToView(0);
        if (column >= 0) {
            list.getColumnModel().getColumn(column).setCellRenderer(renderer);
        }
        rows.reconcile();
    }

    private Optional<Icon> iconFor(MCListItemInterface<SearchResult> item, int side) {
        if (!ThumbnailUiUtils.isEligible(profile)) {
            return Optional.empty();
        }
        requestFor(item, side, list.getGraphicsConfiguration(), false);
        return rows.lookup(item, side).or(() -> Optional.of(fallback.derive(side, side)));
    }

    private Optional<BufferedImage> smallImage(MCListItemInterface<SearchResult> item) {
        return rows.lookupImage(item, list.getRowHeight());
    }

    private Optional<RemoteThumbnailCache.Request> requestFor(MCListItemInterface<SearchResult> item,
            int side, GraphicsConfiguration configuration, boolean allowResolution) {
        if (closed || type == null || !ThumbnailUiUtils.isEligible(profile)) {
            return Optional.empty();
        }
        int size = ThumbnailUiUtils.devicePixelSize(side, configuration);
        if (size == 0) {
            return Optional.empty();
        }
        SearchResult result = item.getObject();
        RemoteThumbnailCache.Request request = cache.resolve(new RemoteThumbnailCache.Request(
                result.getHostAddress(), type, result.getName(), size, Optional.ofNullable(keys.get(item))));
        request.expectedKey().ifPresent(key -> keys.put(item, key));
        return Optional.of(request);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        list.getModel().removeTableModelListener(structureListener);
        hover.close();
        rows.close();
        cache.close();
        keys.clear();
    }
}

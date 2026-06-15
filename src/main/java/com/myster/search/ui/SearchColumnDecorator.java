package com.myster.search.ui;

import com.general.mclist.AbstractMCListItemInterface;
import com.general.mclist.ColumnSortable;
import com.general.mclist.MCListItemInterface;
import com.general.mclist.Sortable;
import com.general.mclist.SortableString;
import com.myster.client.ui.FileListerThread.FileRecord;
import com.myster.search.SearchResult;

/**
 * Wraps a {@link FileTypeColumnHandler} and appends two search-context columns —
 * "Server" at index N and "Ping" at index N+1 (where N = wrapped handler's column count).
 *
 * <p>This is a decorator, not a subclass, so search-context knowledge stays fully outside
 * the type handler hierarchy. Used by {@link SearchTab} to configure its MCList before
 * each search.
 */
public class SearchColumnDecorator implements FileTypeColumnHandler {
    private static final String[] SEARCH_HEADERS = { "Server", "Ping" };
    private static final int[]    SEARCH_WIDTHS  = { 150, 70 };

    private final FileTypeColumnHandler wrapped;
    private final int typeColumnCount;

    /**
     * @param wrapped the per-type handler whose columns are presented first
     */
    public SearchColumnDecorator(FileTypeColumnHandler wrapped) {
        this.wrapped = wrapped;
        this.typeColumnCount = wrapped.getColumnCount();
    }

    @Override
    public int getColumnCount() {
        return typeColumnCount + SEARCH_HEADERS.length;
    }

    @Override
    public String getHeader(int i) {
        return i < typeColumnCount ? wrapped.getHeader(i) : SEARCH_HEADERS[i - typeColumnCount];
    }

    @Override
    public int getHeaderSize(int i) {
        return i < typeColumnCount ? wrapped.getHeaderSize(i) : SEARCH_WIDTHS[i - typeColumnCount];
    }

    /**
     * Returns an item that delegates columns 0..N-1 to the wrapped handler's item and
     * handles column N (server name) and N+1 (ping latency) from the search result.
     */
    @Override
    public MCListItemInterface<SearchResult> getSearchItem(SearchResult s) {
        MCListItemInterface<SearchResult> typeItem = wrapped.getSearchItem(s);
        SortableString serverString = new SortableString(
                s.getServer() == null ? "N/A" : s.getServer().getServerName());
        SortablePing ping = new SortablePing(s.getProtocol(), s.getHostAddress());

        return new AbstractMCListItemInterface<SearchResult>() {
            @Override
            public Sortable getValueOfColumn(int index) {
                if (index < typeColumnCount) return typeItem.getValueOfColumn(index);
                return switch (index - typeColumnCount) {
                    case 0 -> serverString;
                    case 1 -> ping;
                    default -> throw new RuntimeException("Column " + index + " doesn't exist");
                };
            }

            @Override
            public SearchResult getObject() {
                return s;
            }
        };
    }

    /**
     * Delegates to the wrapped handler. Not called from {@code SearchTab}, but
     * required by the interface so this decorator is a complete {@link FileTypeColumnHandler}.
     */
    @Override
    public ColumnSortable<String> getFileItem(FileRecord record) {
        return wrapped.getFileItem(record);
    }

    /**
     * Delegates to the wrapped handler. Not called from {@code SearchTab}, but
     * required by the interface so this decorator is a complete {@link FileTypeColumnHandler}.
     */
    @Override
    public ColumnSortable<String> getFolderItem(String folderName) {
        return wrapped.getFolderItem(folderName);
    }
}


package com.myster.search.ui;

import com.general.mclist.ColumnSortable;
import com.general.mclist.MCListItemInterface;
import com.myster.client.ui.FileListerThread.FileRecord;
import com.myster.search.SearchResult;

/**
 * Describes the per-type columns for a Myster file list — "File Name", "File Size", and any
 * type-specific extras (e.g. MPG3 tag fields). Search-context columns such as "Server" and "Ping"
 * are intentionally excluded; they are appended by {@link SearchColumnDecorator} only when
 * displaying results in the search window.
 *
 * <p>Implementations are returned by {@link ClientInfoFactoryUtilities#getHandler} and may be
 * used by both {@code SearchTab} (via the decorator) and {@code ClientWindow} directly.
 */
public interface FileTypeColumnHandler {

    /** Returns the number of type-specific columns (excludes "Server" and "Ping"). */
    int getColumnCount();

    /**
     * Returns the display name for column {@code index}.
     *
     * @param index zero-based column index, must be &lt; {@link #getColumnCount()}
     */
    String getHeader(int index);

    /**
     * Returns the preferred pixel width for column {@code index}.
     *
     * @param index zero-based column index, must be &lt; {@link #getColumnCount()}
     */
    int getHeaderSize(int index);

    /**
     * Creates an MCList item for a search result row. The item's
     * {@code getValueOfColumn(i)} covers indices 0 through {@link #getColumnCount()}-1.
     * Used by {@code SearchTab} (wrapped inside {@link SearchColumnDecorator}).
     *
     * @param s the search result to wrap
     */
    MCListItemInterface<SearchResult> getSearchItem(SearchResult s);

    /**
     * Creates a sortable cell provider for a file row in the {@code ClientWindow} file list.
     * Column 0 is the file name; column 1 is the file size read from metadata; extra columns
     * (index &ge; 2) carry type-specific metadata values.
     *
     * @param record the file record from the server
     */
    ColumnSortable<String> getFileItem(FileRecord record);

    /**
     * Creates a sortable cell provider for a folder row in the {@code ClientWindow} file list.
     * Column 0 is the folder name; column 1 is a placeholder (overridden by
     * {@code FolderMCListItem} with the accumulated folder size); extra columns
     * (index &ge; 2) return {@code "-"}.
     *
     * @param folderName the bare folder name (not a full path)
     */
    ColumnSortable<String> getFolderItem(String folderName);
}


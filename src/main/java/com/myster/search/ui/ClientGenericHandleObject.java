package com.myster.search.ui;

import com.general.mclist.AbstractMCListItemInterface;
import com.general.mclist.ColumnSortable;
import com.general.mclist.MCListItemInterface;
import com.general.mclist.Sortable;
import com.general.mclist.SortableByte;
import com.general.mclist.SortableString;
import com.myster.client.ui.FileListerThread.FileRecord;
import com.myster.search.SearchResult;

/**
 * Generic (non-type-specific) column handler covering "File Name" and "File Size".
 * Does not include search-context columns ("Server", "Ping") — those are appended by
 * {@link SearchColumnDecorator}.
 */
public class ClientGenericHandleObject implements FileTypeColumnHandler {
    protected final String[] headerarray = { "File Name", "File Size" };

    protected final int[] headerSize = { 300, 70 };

    protected final String[] keyarray = { "n/a", "/size" };

    public String getHeader(int index) {
        return headerarray[index];
    }

    public int getHeaderSize(int index) {
        return headerSize[index];
    }

    public int getColumnCount() {
        return headerarray.length;
    }

    @Override
    public MCListItemInterface<SearchResult> getSearchItem(SearchResult s) {
        return new GenericSearchItem(s);
    }

    @Override
    public ColumnSortable<String> getFileItem(FileRecord record) {
        return new ColumnSortable<String>() {
            public Sortable getValueOfColumn(int column) {
                return switch (column) {
                    case 0 -> new SortableString(record.file());
                    case 1 -> new SortableByte(record.metaData().getLong("/size").orElse(0L));
                    default -> throw new RuntimeException("Column " + column + " doesn't exist");
                };
            }
            public String getObject() { return record.file(); }
        };
    }

    @Override
    public ColumnSortable<String> getFolderItem(String folderName) {
        return new ColumnSortable<String>() {
            public Sortable getValueOfColumn(int column) {
                return switch (column) {
                    case 0 -> new SortableString(folderName);
                    case 1 -> new SortableByte(-2); // placeholder; overridden by FolderMCListItem
                    default -> throw new RuntimeException("Column " + column + " doesn't exist");
                };
            }
            public String getObject() { return folderName; }
        };
    }

    protected class GenericSearchItem extends AbstractMCListItemInterface<SearchResult> {
        private static final SortableByte NOT_IN = new SortableByte(-1);
        private static final SortableByte NUMBER_ERR = new SortableByte(-2);

        protected final SearchResult result;
        private final SortableString sortableName;
        private SortableByte sortableSize;

        public GenericSearchItem(SearchResult s) {
            result = s;
            sortableName = new SortableString(result.getName());
        }

        public Sortable getValueOfColumn(int index) {
            switch (index) {
            case 0:
                return sortableName;
            case 1:
                String size = result.getMetaData(keyarray[1]);
                try {
                    if (size != null && sortableSize == null) {
                        sortableSize = new SortableByte(Integer.parseInt(size));
                    }
                    return (size == null ? NOT_IN : sortableSize);
                } catch (NumberFormatException ex) {
                    return NUMBER_ERR;
                }
            default:
                throw new RuntimeException("Requested a column that doesn't exist.");
            }
        }

        public SearchResult getObject() {
            return result;
        }
    }
}
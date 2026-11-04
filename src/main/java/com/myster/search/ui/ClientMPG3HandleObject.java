package com.myster.search.ui;

import com.general.mclist.ColumnSortable;
import com.general.mclist.MCListItemInterface;
import com.general.mclist.Sortable;
import com.general.mclist.SortableString;
import com.myster.client.ui.FileListerThread.FileRecord;
import com.myster.mml.ReadOnlyMessagePak;
import com.myster.search.SearchResult;

/** Audio columns shared by search and browsing, using typed metadata for numeric sorting. */
public class ClientMPG3HandleObject extends ClientGenericHandleObject {
    protected String[] headerarray = { "Bit Rate", "Hz", "Song Title",
            "Artist", "Album", "Length" };

    protected String[] keyarray = { "/BitRate", "/Hz", "/ID3Name", "/Artist",
            "/Album", "/LengthSec" };

    protected int[] headerSize = { 100, 100, 100, 100, 100, 70 };

    private int numOfColumns;

    public ClientMPG3HandleObject() {
        super();
        numOfColumns = super.getColumnCount();
    }

    public int getColumnCount() {
        return headerarray.length + super.getColumnCount();
    }

    public String getHeader(int index) {
        if (index < super.getColumnCount()) {
            return super.getHeader(index);
        } else {
            return headerarray[index - super.getColumnCount()];
        }
    }

    public int getHeaderSize(int index) {
        if (index < super.getColumnCount()) {
            return super.getHeaderSize(index);
        } else {
            return headerSize[index - super.getColumnCount()];
        }
    }

    @Override
    public MCListItemInterface<SearchResult> getSearchItem(SearchResult s) {
        return new MPG3SearchItem(s);
    }

    @Override
    public ColumnSortable<String> getFileItem(FileRecord record) {
        ColumnSortable<String> base = super.getFileItem(record);
        return new ColumnSortable<String>() {
            public Sortable getValueOfColumn(int column) {
                if (column < numOfColumns) {
                    return base.getValueOfColumn(column);
                }

                return audioColumn(column - numOfColumns, record.metaData(), true);
            }

            public String getObject() {
                return record.file();
            }
        };
    }

    @Override
    public ColumnSortable<String> getFolderItem(String folderName) {
        ColumnSortable<String> base = super.getFolderItem(folderName);
        return new ColumnSortable<String>() {
            public Sortable getValueOfColumn(int column) {
                if (column < numOfColumns) {
                    return base.getValueOfColumn(column);
                }

                return new SortableString("-");
            }
            public String getObject() {
                return folderName;
            }
        };
    }

    /** Reads audio's long-valued numeric fields and string-valued tags for both row contexts. */
    private Sortable audioColumn(int column, ReadOnlyMessagePak metadata, boolean normalizeBlank) {
        return switch (column) {
            case 0 -> new SortableBit(metadata.getLong(keyarray[column]).orElse(-1L));
            case 1 -> new SortableHz(metadata.getLong(keyarray[column]).orElse(-1L));
            case 2, 3, 4 -> {
                String value = metadata.getString(keyarray[column]).orElse("-");
                yield new SortableString(normalizeBlank && value.isBlank() ? "-" : value);
            }
            case 5 -> new SortableLength(metadata.getLong(keyarray[column]).orElse(-1L));
            default -> throw new RuntimeException("Column " + column + " doesn't exist");
        };
    }

    private class MPG3SearchItem extends GenericSearchItem {
        public MPG3SearchItem(SearchResult s) {
            super(s);
        }

        @Override
        public Sortable getValueOfColumn(int index) {
            if (index < numOfColumns) {
                return super.getValueOfColumn(index);
            }

            return audioColumn(index - numOfColumns, result.getMetaData(), false);
        }
    }
}

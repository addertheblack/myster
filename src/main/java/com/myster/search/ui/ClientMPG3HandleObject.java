package com.myster.search.ui;

import com.general.mclist.ColumnSortable;
import com.general.mclist.MCListItemInterface;
import com.general.mclist.Sortable;
import com.general.mclist.SortableString;
import com.myster.client.ui.FileListerThread.FileRecord;
import com.myster.search.SearchResult;

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
                if (column < numOfColumns) return base.getValueOfColumn(column);
                int extra = column - numOfColumns;
                return switch (extra) {
                    case 0 -> {
                        try { yield new SortableBit(Long.parseLong(record.metaData().get(keyarray[extra]).orElse("-1"))); }
                        catch (Exception e) { yield new SortableBit(-1); }
                    }
                    case 1 -> {
                        try { yield new SortableHz(Long.parseLong(record.metaData().get(keyarray[extra]).orElse("-1"))); }
                        catch (Exception e) { yield new SortableHz(-1); }
                    }
                    case 2, 3, 4 -> {
                        String v = record.metaData().get(keyarray[extra]).orElse("-");
                        yield new SortableString(v.isBlank() ? "-" : v);
                    }
                    case 5 -> {
                        try {
                            yield new SortableLength(Long.parseLong(record.metaData()
                                    .get(keyarray[extra])
                                    .orElse("-1")));
                        } catch (NumberFormatException e) {
                            yield new SortableLength(-1);
                        }
                    }
                    default -> throw new RuntimeException("Column " + column + " doesn't exist");
                };
            }
            public String getObject() { return record.file(); }
        };
    }

    @Override
    public ColumnSortable<String> getFolderItem(String folderName) {
        ColumnSortable<String> base = super.getFolderItem(folderName);
        return new ColumnSortable<String>() {
            public Sortable getValueOfColumn(int column) {
                if (column < numOfColumns) return base.getValueOfColumn(column);
                return new SortableString("-");
            }
            public String getObject() { return folderName; }
        };
    }

    private class MPG3SearchItem extends GenericSearchItem {
        public MPG3SearchItem(SearchResult s) {
            super(s);
        }

        public Sortable getValueOfColumn(int index) {
            if (index < numOfColumns) {
                return super.getValueOfColumn(index);
            } else {
                int newIndex = index - numOfColumns;
                switch (newIndex) {
                case 0:
                    try {
                        return new SortableBit(Long.parseLong(result
                                .getMetaData(keyarray[newIndex]))); //Lines like
                                                                 // this are the
                                                                 // only reasone
                                                                 // I write
                                                                 // programs.
                    } catch (Exception ex) {
                        return new SortableBit(-1);
                    }

                case 1:
                    try {
                        return new SortableHz(Long.parseLong(result
                                .getMetaData(keyarray[newIndex]))); //Lines like
                                                                 // this are the
                                                                 // only reasone
                                                                 // I write
                                                                 // programs.
                    } catch (Exception ex) {
                        return new SortableHz(-1);
                    }
                case 2: //no break statement so it falls through (cool, eh?)
                case 3: //no break statement so it falls through
                case 4:
                    String s_temp = result.getMetaData(keyarray[newIndex]);
                    return new SortableString(s_temp == null ? "-" : s_temp);

                case 5:
                    try {
                        return new SortableLength(Long.parseLong(result
                                .getMetaData(keyarray[newIndex])));
                    } catch (NumberFormatException ex) {
                        return new SortableLength(-1);
                    }

                default:
                    // This should crash the thread nicely.
                    throw new RuntimeException("This column doesn't exist");
                }
            }
        }
    }
}

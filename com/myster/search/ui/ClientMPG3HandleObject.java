package com.myster.search.ui;

import com.general.mclist.MCListItemInterface;
import com.general.mclist.Sortable;
import com.general.mclist.SortableString;
import com.myster.search.SearchResult;

public class ClientMPG3HandleObject extends ClientGenericHandleObject {
    protected String[] headerarray = { "Bit Rate", "Hz", "Song Title",
            "Artist", "Album" };

    protected String[] keyarray = { "/BitRate", "/Hz", "/ID3Name", "/Artist",
            "/Album" };

    protected int[] headerSize = { 100, 100, 100, 100, 100 };

    private int numOfColumns;

    public ClientMPG3HandleObject() {
        super();
        numOfColumns = super.getNumberOfColumns();
    }

    public int getNumberOfColumns() {
        return headerarray.length + super.getNumberOfColumns();
    }

    public String getHeader(int index) {
        if (index < super.getNumberOfColumns()) {
            return super.getHeader(index);
        } else {
            return headerarray[index - super.getNumberOfColumns()];
        }
    }

    public int getHeaderSize(int index) {
        if (index < super.getNumberOfColumns()) {
            return super.getHeaderSize(index);
        } else {
            return headerSize[index - super.getNumberOfColumns()];
        }
    }

    public MCListItemInterface getMCListItem(SearchResult s) { //factory...
                                                               // chugga
                                                               // chugga...
        return new MPG3SearchItem(s);
    }

    private class MPG3SearchItem extends GenericSearchItem {
        SearchResult result;

        public MPG3SearchItem(SearchResult s) {
            super(s);
            result = s;
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

                default:
                    throw new RuntimeException("This column doesn't exist"); //This
                                                                             // should
                                                                             // crash
                                                                             // the
                                                                             // thread
                                                                             // nicely.
                }
            }
        }
    }
}
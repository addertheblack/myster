package com.myster.search.ui;

import com.general.mclist.SortableLong;

public class SortableBit extends SortableLong {

    public SortableBit(long zerg) {
        super(zerg);
    }

    public String toString() {
        if (number < 0)
            return "??";
        return "" + (((Long) getValue()).longValue() / 1000) + "Kbps";
    }

}
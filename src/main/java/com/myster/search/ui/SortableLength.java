package com.myster.search.ui;

import com.general.mclist.SortableLong;

public class SortableLength extends SortableLong {

    public SortableLength(long seconds) {
        super(seconds);
    }

    @Override
    public String toString() {
        if (number < 0) {
            return "-";
        }

        long minutes = number / 60;
        long seconds = number % 60;
        return minutes + ":" + String.format("%02d", seconds);
    }
}

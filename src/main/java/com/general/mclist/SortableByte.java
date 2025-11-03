package com.general.mclist;

import com.general.util.Util;

public class SortableByte extends SortableLong {

    public SortableByte(long zerg) {
        super(zerg);
    }

    public String toString() {
        if (number == -1) {
            return "??";
        } else if (number == -2) {
            return "";
        }
        
        return Util.getStringFromBytes(((Long) getValue()).longValue());
    }

}
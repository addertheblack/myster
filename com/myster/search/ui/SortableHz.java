package com.myster.search.ui;

import com.general.mclist.*;

public class SortableHz extends SortableLong {

	public SortableHz(long zerg) {
		super(zerg);
	}

	public String toString() {
		if (number<0) return "??";
		return ""+(((Long)getValue()).longValue())+"Hz";
	}

}
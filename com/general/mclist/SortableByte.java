package com.general.mclist;
import com.general.util.*;

public class SortableByte extends SortableLong {

	public SortableByte(long zerg) {
		super(zerg);
	}

	public String toString() {
		if (number<0) return "??";
		return Util.getStringFromBytes(((Long)getValue()).longValue());
	}

}
package com.myster.type;

import com.general.events.GenericEvent;

public class TypeDescriptionEvent extends GenericEvent {
	public static final int DISABLE = 0;
	public static final int ENABLE = 1;
	
	TypeDescriptionList list;
	MysterType type;
	
	public TypeDescriptionEvent(int id, TypeDescriptionList list, MysterType type) {
		super(id);
		
		this.list = list;
		this.type = type;
	}
	
	public TypeDescriptionList getList() {
		return list;
	}
	
	public MysterType getType() {
		return type;
	}
}
package com.myster.menubar.event;

import com.general.events.GenericEvent;

public class MenuBarEvent extends GenericEvent {
	public static final int BAR_CHANGED=1;
	
	public MenuBarEvent(int id) {
		super(id);
	}

}
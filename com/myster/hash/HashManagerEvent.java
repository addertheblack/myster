package com.myster.hash;

import com.general.events.GenericEvent;

//immutable
public class HashManagerEvent extends GenericEvent {
	public static final int ENABLED_STATE_CHANGED = 1;

	private final boolean enabled;

	public HashManagerEvent(int id, boolean enabled) {
		super(id);
		
		this.enabled = enabled;
	}
	
	public boolean isEnabled() {
		return enabled;
	}
}
package com.myster.hash;

import java.io.File;

import com.general.events.GenericEvent;

//immutable
public class HashManagerEvent extends GenericEvent {
	public static final int ENABLED_STATE_CHANGED 	= 1;
	public static final int START_HASH				= 2;
	public static final int PROGRESS_HASH			= 3;
	public static final int END_HASH				= 4;

	private final boolean enabled;
	private final File file;
	private final long progress;

	public HashManagerEvent(int id, boolean enabled) {
		this(id,enabled,null,-1);
	}
	
	public HashManagerEvent(int id, boolean enabled, File file, long progress) {
		super(id);
		
		this.enabled 	= enabled;
		this.file		= file;
		this.progress	= progress;
	}
	
	public boolean isEnabled() {
		return enabled;
	}
	
	/**
	*	Returns file being processed or null if not applicable.
	*/
	public File getFile() {
		return file;
	}
	
	/**
	*	Returns the progress through the current file (or -1 if not applicable)
	*/		
	public long getProgress() {
		return progress;
	}
}
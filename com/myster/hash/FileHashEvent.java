package com.myster.hash;

import com.general.events.GenericEvent;

//immutable
public class FileHashEvent extends GenericEvent {
	public static final int FOUND_HASH = 1;

	private FileHash[] hashes;
	
	public FileHashEvent(int id, FileHash[] hashes) {
		super(id);
		
		this.hashes = hashes;
	}
	
	public FileHash[] getHashes() {
		return (FileHash[])hashes.clone();
	}
}
package com.myster.filemanager;

/**
*	File item encapsulates all the File related things like
*	hashes and file info and search matching.
*/

import java.io.File;

public class FileItem {
	private final File file;
	//private FileHash fileHash;

	public FileItem(File file) {
		this.file = file;
	}
	/**
	*	Returns the java.io.File object
	*/
	public File getFile() {
		return file;
	}
	
	/*
	public FileHash getHash(String hashType) {
	
	}
	
	public boolean isHashed() {
	
	}
	
	public String getName() {
	
	}
	
	public String getFullPath() {
	
	}
	
	public boolean isMatch(String queryString) {
	
	}
	
	public MML getMMLRepresentation() {
	
	}
	*/
	
}
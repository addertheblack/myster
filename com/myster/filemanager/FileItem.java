package com.myster.filemanager;

import com.myster.hash.FileHashListener;
import com.myster.hash.FileHashEvent;
import com.myster.hash.HashManager;
import com.myster.hash.FileHash;
import com.myster.mml.MML;

//Immutable
/**
*	File item encapsulates all the File related things like
*	hashes and file info and search matching.
*/

import java.io.File;

public class FileItem {
	private final File file;
	private FileHash[] fileHashes;

	public FileItem(File file) {
		this.file = file;
		
		HashManager.findHashNoneBlocking(file, new FileHashListener() {
			public void foundHash(FileHashEvent e) {
				fileHashes = e.getHashes();
			}
		});
	}
	/**
	*	Returns the java.io.File object
	*/
	public File getFile() {
		return file;
	}
	
	private int getIndex(String hashType) {
		if (fileHashes == null) return -1;
	
		for (int i = 0; i < fileHashes.length; i++) {
			if (fileHashes[i].getHashName().equals(hashType)) return i;
		}
		
		return -1;
	}
	
	private synchronized void setHash(FileHash[] fileHashes) {
		this.fileHashes = fileHashes;
	}

	public synchronized FileHash getHash(String hashType) {
		int index = getIndex(hashType);
		
		if (index == -1) return null;
		
		return fileHashes[index];
	}
	
	/**
	* If the file hash been hashed at some point this returns true.
	*/	
	public boolean isHashed() {
		return (fileHashes != null);
	}
	
	public String getName() {
		return file.getName();
	}
	
	public String getFullPath() {
		return file.getAbsolutePath();
	}
	
	public boolean equals(Object o) {
		try {
			FileItem item = (FileItem)o;
			
			return (file.equals(item.file));
		} catch (ClassCastException ex) {
			return false;
		}
	}

	public static final String HASH_PATH = "/hash/";
	public MML getMMLRepresentation() {
		MML mml=new MML();
		
		if (file!=null) {
			mml.put("/size", ""+file.length());
			
			if (fileHashes != null) {
				for (int i = 0; i < fileHashes.length ; i++) {
					mml.put(HASH_PATH+fileHashes[i].getHashName().toLowerCase(), fileHashes[i].toString());
				}
			}
		}
		
		return mml;
	}
	
	/*
	public boolean isMatch(String queryString) {
	
	}
	*/

}
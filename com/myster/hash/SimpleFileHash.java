package com.myster.hash;

import java.io.Serializable;

public class SimpleFileHash extends FileHash implements Serializable {
	private byte[] hash;
	private String hashName;
	
	private SimpleFileHash(){}
	
	protected SimpleFileHash(String hashName, byte[] hash) {
		this.hashName = hashName;
		this.hash = (byte[])hash.clone();
	}
	
	public byte[] getBytes() {
		return (byte[])hash.clone();
	}
	
	public int getHashLength() {
		return hash.length;
	}
	
	public String getHashName() {
		return hashName;
	}
}

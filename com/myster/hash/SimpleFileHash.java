package com.myster.hash;

public class SimpleFileHash extends FileHash {
	private byte[] hash;
	private String hashName;
	
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

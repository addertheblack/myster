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
	
	public String toString() {
		return asHex(hash);
	}
	
	public static String asHex (byte hash[]) {
	    StringBuffer buf = new StringBuffer(hash.length * 2);
	    int i;

	    for (i = 0; i < hash.length; i++) {
	      if (((int) hash[i] & 0xff) < 0x10) 
		buf.append("0");

	      buf.append(Long.toString((int) hash[i] & 0xff, 16));
	    }

	    return buf.toString();
  	}
  	
  	public static FileHash buildFileHash(String hashName, byte[] hash) {
  		return new SimpleFileHash(hashName, hash);
  	}
}

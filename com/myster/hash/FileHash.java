package com.myster.hash;

public abstract class FileHash {
	public abstract byte[] getBytes() ;
	public abstract int getHashLength() ;
	public abstract String getHashName();
	//toString <- should be hex value
}
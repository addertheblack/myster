package com.myster.type;

import java.io.UnsupportedEncodingException;

public class MysterType {
	final int TYPE_LENGTH = 4;
	final byte[] type;
	
	public MysterType(byte[] type) {
		if (type.length != TYPE_LENGTH) throw new MysterTypeException("Not a Myster Type");
	
		this.type = (byte[]) type.clone();
	}
	
	public boolean equals(Object o) {
		MysterType other = (MysterType)o;
		
		for (int i = 0; i < TYPE_LENGTH; i++) {
			if (other.type[i] != type[i]) return false;
		}
		
		return true;
	}
	
	public int hashCode() {
		int temp = 0;
		for (int i = type.length; i >0; i--) {
			temp <<= 8;
			temp |= ((int)(type[i-1]) & 0xFF);
		}
		
		return temp;
	}
	
	public boolean equals(String o) {
		return o.equals(toString());
	}
	
	public byte[] getBytes() {
		return (byte[])type.clone();
	}
	
	public String toString() {
		try {
			return new String(type, "ascii");
		} catch (UnsupportedEncodingException ex) {
			ex.printStackTrace();
			return new String(type);
		}
	}
}


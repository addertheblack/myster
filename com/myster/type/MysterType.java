/* 

	Title:			Myster Open Source
	Author:			Andrew Trumper
	Description:	Generic Myster Code
	
	This code is under GPL

Copyright Andrew Trumper 2003
*/

package com.myster.type;

import java.io.UnsupportedEncodingException;

public class MysterType {
	final int TYPE_LENGTH = 4;
	final byte[] type;
	
	public MysterType(byte[] type) {
		if (type.length != TYPE_LENGTH) throw new MysterTypeException("Not a Myster Type");
	
		this.type = (byte[]) type.clone();
	}
	
	public MysterType(int type) {
		this(new byte[]{(byte)((type>>24) & 0xFF),
						(byte)((type>>16) & 0xFF),
						(byte)((type>>8) & 0xFF),
						(byte)((type>>0) & 0xFF)});
	}
	
	public boolean equals(Object o) {
		MysterType mysterType;
		
		try {
			mysterType = (MysterType)o;
		} catch (ClassCastException ex) {
			return false;
		}
		
		for (int i=0; i<type.length; i++) {
			if (type[i]!=mysterType.type[i]) return false;
		}
		
		return true;
	}
	
	public int hashCode() {
		return getAsInt();
	}
	
	public int getAsInt() {
		int temp = 0;
		for (int i = 0; i<type.length; i++) {
			temp <<= 8;
			temp |= ((int)(type[i]) & 0xFF);
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
			return new String(type, "ASCII");
		} catch (UnsupportedEncodingException ex) {
			ex.printStackTrace();
			return new String(type);
		}
	}
}


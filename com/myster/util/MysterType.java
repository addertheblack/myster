package com.myster.util;

import java.io.UnsupportedEncodingException;

public class MysterType {
	private static final String ASCII_ENCODING = "ASCII";
	private byte[] type;
	
	public MysterType(byte[] bytes) {
		init(bytes);
	}
	
	public MysterType(String type) {
		try {
			init(type.getBytes(ASCII_ENCODING));
		} catch (UnsupportedEncodingException ex) {
			ex.printStackTrace();
		}
	}
	
	private void init(byte[] bytes) {
		if (bytes.length>4 || bytes.length<4) throw new NotAMysterTypeException("Length should be 4 but is "+bytes.length);
		
		type= bytes;
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
	
	public String toString() {
		try {
			return new String(type,ASCII_ENCODING);
		} catch (UnsupportedEncodingException ex) {
			ex.printStackTrace();
			return "<Error>";
		}
	}
}


public class NotAMysterTypeException extends RuntimeException {

	public NotAMysterTypeException(String message) {
		super(message);
	}
}
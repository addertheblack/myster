package com.myster.mml;


public class InvalidTokenException extends MMLRuntimeException {
	public InvalidTokenException(String s) {
		super("Invalid token: "+s);
	}
}
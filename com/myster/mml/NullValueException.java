package com.myster.mml;


	
public class NullValueException extends MMLRuntimeException {
	public NullValueException() {
		super("null and empty string are not valid values");
	}
}
package com.myster.net;

import java.io.IOException;

public class DisconnectException extends IOException {
	public DisconnectException(String s) {
		super(s);
	}
	
	public DisconnectException() {
		super("Was told to disconnect.");
	}
}
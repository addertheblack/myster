package com.myster.net;

import java.io.IOException;

public class BadPacketException extends IOException {
	public BadPacketException(String s) {
		super(s);
	}
}
	
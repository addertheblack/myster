package com.myster.mml;

public class NoStartingSlashException extends MMLPathException {
	public NoStartingSlashException(String path) {
		super("No starting slash Exception: "+path);
	}
}
package com.myster.mml;


	
public class NonExistantPathException extends MMLException {
	public NonExistantPathException(String path) {
		super("Path "+path+" does not exist");
	}
}
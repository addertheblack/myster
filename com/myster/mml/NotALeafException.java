package com.myster.mml;


public class NotALeafException extends MMLException {
	public NotALeafException() {
		super("Node is not a leaf node");
	}
}
package com.myster.mml;

public class BranchAsALeafException extends MMLPathException {
	public BranchAsALeafException(String s) {
		super(s+" | (Tried to access branch as a leaf)");
	}
}
package com.myster.mml;

public class NotABranchException extends MMLException {
	public NotABranchException() {
		super("Node is not a branch node");
	}
	
	public NotABranchException(String s) {
		super(s);
	}
}
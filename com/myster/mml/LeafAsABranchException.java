package com.myster.mml;

	
public class LeafAsABranchException extends MMLPathException {
	public LeafAsABranchException(String s) {
		super(s+" | (Tried to access leaf as a branch)");
	}
}
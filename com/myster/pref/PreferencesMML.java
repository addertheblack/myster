package com.myster.pref;

import com.myster.mml.RobustMML;
import com.myster.mml.MMLException;
import com.myster.mml.MML;

public class PreferencesMML extends RobustMML {
	public PreferencesMML() {
		super();
	}
	
	
	public PreferencesMML(String s) throws MMLException{//THROWS NullPointerException if argument is null
		super(s);
	}
	
	public PreferencesMML(MML mml) { //turns an MML into a robust MML with 0 loss in performance.
		super(mml);
	}


	
	/**
		Removes the value at key path. All empty branch nodes along the path are deleted.
		Returns the value at key path. If path is invalid does not delete anything and returns defaultValue.
	*/
	public String remove(String path, String defaultValue) {
		String temp=remove(path);
		
		return (temp==null?defaultValue:temp);
	}
	
	/**
		Gets the value at key path. If path doens't exist, returns defaultValue.
	*/
	public String get(String path, String defaultValue) {
		String temp=get(path);
		
		return (temp==null?defaultValue:temp);
	}
}
package com.myster.pref.ui;

import java.awt.LayoutManager;
import java.awt.Panel;

/**
*	All modules wishing to make use of the preferences GUI should 
*	extend from this abstract class. Standard Panel size is
*	STD_XSIZE by STD_YSIZE. This is static for the duration of the program.
*	It might not be its current value in the future, however it should not
*	be SMALLER.
*	
*	The save function is called by the Preferences Window 
*	when the user clicks on the save button.
*	
*	The choice about whether a pluggin should apply the settings
*	imeadiatly or wait until restart is left up to the pluggin however
*	it's considered lame if the settings apply only on restart.
*/

public abstract class PreferencesPanel extends Panel {
	public static final int STD_XSIZE=450;
	public static final int STD_YSIZE=300;
	
	
	/**
	*	Wrapper.. See java.awt.Panel.
	*/
	public PreferencesPanel() {
		super();
	}
	
	/**
	*	Wrapper.. See java.awt.Panel.
	*/
	public PreferencesPanel(LayoutManager l) {
		super(l);
	}
	
	public abstract void save();	//save changes
	public abstract void reset();	//discard changes and reset values to their defaults.
	public abstract String getKey();//gets the key structure for the place in the pref panel
}
package com.myster.pref.ui;

import java.awt.Frame;
import java.awt.LayoutManager;
import java.awt.Panel;

/**
 * All modules wishing to make use of the preferences GUI should extend from
 * this abstract class. Standard Panel size is STD_XSIZE by STD_YSIZE. This is
 * static for the duration of the program. It might not be its current value in
 * the future, however it should not be SMALLER.
 * 
 * The save function is called by the Preferences Window when the user clicks on
 * the save button.
 * 
 * The choice about whether a pluggin should apply the settings imeadiatly or
 * wait until restart is left up to the pluggin however it's considered lame if
 * the settings apply only on restart.
 */

public abstract class PreferencesPanel extends Panel {
    public static final int STD_XSIZE = 450;

    public static final int STD_YSIZE = 300;

    Frame parentFrame;

    /**
     * Wrapper.. See java.awt.Panel.
     */
    public PreferencesPanel() {
        super();
    }

    /**
     * Wrapper.. See java.awt.Panel.
     */
    public PreferencesPanel(LayoutManager l) {
        super(l);
    }

    /**
     * Tells this panel to commit the changes that ave been made.
     *  
     */
    public abstract void save(); //save changes

    /**
     * Tells this panel to throw away any changes that have been made and to
     * re-read the state from the prefs.
     *  
     */
    public abstract void reset(); //discard changes and reset values to their

    /**
     * Gets the key that will be the name of this panel in the prefs.
     * 
     * @return the name of this panel to apear in the preferences window.
     */
    public abstract String getKey();//gets the key structure for the place in

    // the pref panel

    protected final void addFrame(Frame frame) {
        parentFrame = frame;
    }

    /**
     * Returns the Frame containing this preference panel. Sub classes should
     * not assume that this value is not null if the panel has been added.
     * 
     * @return the window containing this preference panel.
     */

    public Frame getFrame() {
        return parentFrame; //returns the frame that this panel is in else
        // null;
    }
}
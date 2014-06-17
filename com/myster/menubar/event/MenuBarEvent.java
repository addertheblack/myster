package com.myster.menubar.event;

import java.awt.Frame;

import javax.swing.JMenuBar;

import com.general.events.GenericEvent;
import com.myster.menubar.MysterMenuBarFactory;

/**
 * Contains the events context for MysterMenuBar events.
 */
public class MenuBarEvent extends GenericEvent {
    public static final int BAR_CHANGED = 1;

    MysterMenuBarFactory factory;

    public MenuBarEvent(int id, MysterMenuBarFactory factory) {
        super(id);
        this.factory = factory;
    }

    /**
     * Asks the contained factory to build a new copy of the menu bar
     * specifically for his Frame. This routine does not add the menubar to the
     * frame.
     * 
     * @param frame
     *            to tailor the event for.
     * @return the menubar.
     */
    public JMenuBar makeNewMenuBar(Frame frame) {
        return factory.makeMenuBar(frame);
    }

}
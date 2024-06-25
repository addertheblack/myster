package com.myster.ui.menubar.event;

import javax.swing.JFrame;
import javax.swing.JMenuBar;

import com.myster.ui.menubar.MysterMenuBarFactory;

/**
 * Contains the events context for MysterMenuBar events.
 */
public class MenuBarEvent {
    private final MysterMenuBarFactory factory;

    public MenuBarEvent(MysterMenuBarFactory factory) {
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
    public JMenuBar makeNewMenuBar(JFrame frame) {
        return factory.makeMenuBar(frame);
    }

}
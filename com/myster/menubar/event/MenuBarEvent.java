package com.myster.menubar.event;

import java.awt.MenuBar;

import com.general.events.GenericEvent;
import com.myster.menubar.MysterMenuBarFactory;

public class MenuBarEvent extends GenericEvent {
    public static final int BAR_CHANGED = 1;

    MysterMenuBarFactory factory;

    public MenuBarEvent(int id, MysterMenuBarFactory factory) {
        super(id);
        this.factory = factory;
    }

    //is slow.
    public MenuBar makeNewMenuBar() {
        return factory.makeMenuBar();
    }

}
package com.myster.menubar.event;

import com.general.events.EventListener;
import com.general.events.GenericEvent;

public abstract class MenuBarListener extends EventListener {

    public void fireEvent(GenericEvent event) {
        MenuBarEvent e = (MenuBarEvent) event;
        switch (e.getID()) {
        case MenuBarEvent.BAR_CHANGED:
            stateChanged(e);
            break;
        default:
            err();
        }
    }

    public abstract void stateChanged(MenuBarEvent e);

}
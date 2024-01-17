package com.myster.ui.menubar.event;

import com.general.events.EventListener;
import com.general.events.GenericEvent;

/**
 * Interface to override to listen for MenuBarEvents from Myster's global menu
 * bar.
 */
public abstract class MenuBarListener implements EventListener {

    public final void fireEvent(GenericEvent event) {
        MenuBarEvent e = (MenuBarEvent) event;
        switch (e.getID()) {
        case MenuBarEvent.BAR_CHANGED:
            stateChanged(e);
            break;
        default:
            err();
        }
    }

    /**
     * Is called if event is an BAR_CHANGED event. BAR_CHANGED events are thrown
     * if a menu item or menu is added or removed.
     * 
     * @param e events containing some context for this event.
     */
    public abstract void stateChanged(MenuBarEvent e);

}
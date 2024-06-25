package com.myster.ui.menubar.event;

/**
 * Interface to override to listen for MenuBarEvents from Myster's global menu
 * bar.
 */
public interface MenuBarListener {
    /**
     * Is called if event is an BAR_CHANGED event. BAR_CHANGED events are thrown
     * if a menu item or menu is added or removed.
     * 
     * @param e events containing some context for this event.
     */
    public void stateChanged(MenuBarEvent e);
}
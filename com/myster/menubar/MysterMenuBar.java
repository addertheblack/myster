/* 

 Title:			Myster Open Source
 Author:		Andrew Trumper
 Description:	Generic Myster Code
 
 This code is under GPL

 Copyright Andrew Trumper 2000-2004
 */

package com.myster.menubar;

import java.awt.MenuBar;
import java.util.List;
import java.util.Vector;

import javax.swing.JFrame;
import javax.swing.JMenuBar;

import com.general.events.AsyncEventThreadDispatcher;
import com.general.events.EventDispatcher;
import com.myster.menubar.event.AddIPMenuAction;
import com.myster.menubar.event.CloseWindowAction;
import com.myster.menubar.event.MenuBarEvent;
import com.myster.menubar.event.MenuBarListener;
import com.myster.menubar.event.NewClientWindowAction;
import com.myster.menubar.event.NewSearchWindowAction;
import com.myster.menubar.event.NullAction;
import com.myster.menubar.event.PreferencesAction;
import com.myster.menubar.event.QuitMenuAction;
import com.myster.menubar.event.StatsWindowAction;
import com.myster.menubar.event.TrackerWindowAction;
import com.myster.tracker.IPListManager;
import com.myster.ui.PreferencesGui;

/**
 * Is the global Myster MenuBar. NOTE: On the macintosh it's important to have a
 * global menu bar. The menu bar object has a groovy constructor that works with
 * the MysterMenuObject in order to make the taks of adding new menu items easy.
 * 
 * Warning this code is not even close to being thread safe. Only call it on the
 * event thread.
 */

public class MysterMenuBar extends MenuBar {
    private static final NullAction NULL = new NullAction();

    /** Static sub-system is below */
    static EventDispatcher dispatcher = new AsyncEventThreadDispatcher();

    private static MysterMenuBarFactory impl;

    private static Vector file, edit, special, menuBar, plugins;

    private static MysterMenuFactory pluginMenuFactory;

    private static IPListManager manager;

    private static PreferencesGui prefGui;
    
    private static boolean inited = false;

    public static void init(IPListManager manager, PreferencesGui prefGui) {
        MysterMenuBar.manager = manager;
        MysterMenuBar.prefGui = prefGui;
        inited = true;
        
        updateMenuBars();
    }
    
    /**
     * Creates a factory which can be used to build identical menubars for
     * different frames..
     * 
     * NOTE: THIS METHOD MUST BE CALLED FROM THE EVENT THREAD.
     * 
     * @return the Menubar factory.
     */
    public static MysterMenuBarFactory getFactory() {
        if (inited && impl == null) {
            file = new Vector();
            edit = new Vector();
            special = new Vector();

            // File menu items

            file.addElement(new MysterMenuItemFactory("New Search",
                                                      new NewSearchWindowAction(),
                                                      java.awt.event.KeyEvent.VK_N));
            file.addElement(new MysterMenuItemFactory("New Peer-to-Peer Connection",
                                                      new NewClientWindowAction(),
                                                      java.awt.event.KeyEvent.VK_N,
                                                      true));
            file.addElement(new MysterMenuItemFactory("New Instant Message",
                                                      (java.awt.event.ActionEvent e) -> {
                                                          com.myster.message.MessageWindow window =
                                                                  new com.myster.message.MessageWindow();
                                                          window.setVisible(true);
                                                      }));
            file.addElement(new MysterMenuItemFactory("Close Window",
                                                      new CloseWindowAction(),
                                                      java.awt.event.KeyEvent.VK_W));
            file.addElement(new MysterMenuItemFactory("-", NULL));

            file.addElement(new MysterMenuItemFactory("Quit",
                                                      new QuitMenuAction(),
                                                      java.awt.event.KeyEvent.VK_Q));

            // Edit menu items
            edit.addElement(new MysterMenuItemFactory("Undo", NULL));
            edit.addElement(new MysterMenuItemFactory("-", NULL));
            edit.addElement(new MysterMenuItemFactory("Cut", NULL));
            edit.addElement(new MysterMenuItemFactory("Copy (use command-c)", NULL));
            edit.addElement(new MysterMenuItemFactory("Paste (use command-v)", NULL));
            edit.addElement(new MysterMenuItemFactory("Clear", NULL));
            edit.addElement(new MysterMenuItemFactory("-", NULL));
            edit.addElement(new MysterMenuItemFactory("Select All", NULL));
            edit.addElement(new MysterMenuItemFactory("-", NULL));
            edit.addElement(new MysterMenuItemFactory("Preferences",
                                                      new PreferencesAction(MysterMenuBar.prefGui),
                                                      java.awt.event.KeyEvent.VK_SEMICOLON));

            // Disable all Edit menu commands
            for (int i = 0; i < edit.size() - 1; i++) {
                ((MysterMenuItemFactory) (edit.elementAt(i))).setEnabled(false);
            }

            // Myster menu items
            special.addElement(new MysterMenuItemFactory("Add IP", new AddIPMenuAction(manager)));
            special.addElement(new MysterMenuItemFactory("Show Server Stats",
                                                         new StatsWindowAction(),
                                                         java.awt.event.KeyEvent.VK_S,
                                                         true));
            special.addElement(new MysterMenuItemFactory("Show Tracker",
                                                         new TrackerWindowAction(),
                                                         java.awt.event.KeyEvent.VK_T));
            special.addElement(com.myster.hash.ui.HashManagerGUI.getMenuItem());

            // Myster plugins Menu
            plugins = new Vector();
            pluginMenuFactory = new MysterMenuFactory("Plugins", plugins);

            menuBar = new Vector();
            menuBar.addElement(new MysterMenuFactory("File", file));
            menuBar.addElement(new MysterMenuFactory("Edit", edit));
            menuBar.addElement(new MysterMenuFactory("Special", special));
            // plugins menu is not added here.

            impl = new DefaultMysterMenuBarFactory(menuBar);
        } else if (!inited ){
            return new MysterMenuBarFactory() {
                // nothing to do
            };
        }

        return impl;
    }
    public static class DefaultMysterMenuBarFactory implements MysterMenuBarFactory {
        private List<MysterMenuFactory> mysterMenuFactories;

        public DefaultMysterMenuBarFactory(List<MysterMenuFactory> mysterMenuFactories) {
            this.mysterMenuFactories = mysterMenuFactories;
        }

        public JMenuBar makeMenuBar(JFrame frame) {
            JMenuBar menuBar = new JMenuBar();

            for (int i = 0; i < mysterMenuFactories.size(); i++) {
                menuBar.add(mysterMenuFactories.get(i).makeMenu(frame));
            }

            return menuBar;
        }

        public int getMenuCount() {
            return mysterMenuFactories.size();
        }
    }

    /**
     * Adds a MenuBarListener.
     * 
     * This method is thread safe.
     * 
     * @param listener
     *            to add.
     */
    public static void addMenuListener(MenuBarListener listener) { //Not
        dispatcher.addListener(listener);
    }

    /**
     * Removes a MenuBarListener.
     * 
     * This method is thread safe.
     * 
     * @param listener
     *            to remove.
     */
    public static void removeMenuListener(MenuBarListener listener) { //Not
        dispatcher.removeListener(listener);
    }

    /**
     * Removes a built in menu by name.
     * 
     * @param menuName
     *            of the built in menu to remove.
     * @return returns true if menu was found and removed, false otherwise.
     */
    public static boolean removeBuiltInMenu(String menuName) {
        getFactory(); //assert menu bar stuff is loaded.
        for (int i = 0; i < menuBar.size(); i++) {
            if (((MysterMenuFactory) (menuBar.elementAt(i))).getName().equalsIgnoreCase(menuName)) {
                menuBar.removeElementAt(i);
                updateMenuBars();
                return true;
            }
        }
        return false;
    }

    /**
     * Removes a built in menu by menu item name and menu name.
     * 
     * @param menuName
     *            of the menu to remove
     * @param menuItem
     *            the name of the menu containing the menuitem to remove.
     * @return true if the menu item was found and removed, false otherwise.
     */
    public static boolean removeBuiltInMenuItem(String menuName, String menuItem) {
        if (menuItem.equals("-"))
            return false;

        if (menuName.equalsIgnoreCase("File")) {
            return removeMenuItem(file, menuItem);
        } else if (menuName.equalsIgnoreCase("Edit")) {
            return removeMenuItem(edit, menuItem);
        } else if (menuName.equalsIgnoreCase("Special")) {
            return removeMenuItem(special, menuItem);
        }

        return false;
    }

    private static boolean removeMenuItem(Vector vector, String menuItem) { //ugh...
        getFactory(); //assert menu bar stuff is loaded.

        for (int i = 0; i < vector.size(); i++) {
            MysterMenuItemFactory item = (MysterMenuItemFactory) (vector.elementAt(i));
            if (item.getName().equalsIgnoreCase(menuItem)) {
                vector.removeElementAt(i);
                while ((vector.size() > 0)
                        && (((MysterMenuItemFactory) (vector.elementAt(vector.size() - 1)))
                                .getName().equals("-"))) {
                    vector.removeElementAt(vector.size() - 1);
                }

                updateMenuBars();

                return true;
            }
        }

        return false;
    }

    /**
     * Update menubars signals that a change in the menus has taken place and
     * that all MenuBarListeners should update their menus to reflect the change
     * 
     * This routine is not blocking.
     */
    private static void updateMenuBars() {
        dispatcher.fireEvent(new MenuBarEvent(MenuBarEvent.BAR_CHANGED, getFactory()));
    }

    /**
     * Adds a menu to the end of the menubar.
     * 
     * @param factory
     *            that will create the menu.
     */
    public static void addMenu(MysterMenuFactory factory) {
        getFactory(); //assert menu bar stuff is loaded.

        menuBar.addElement(factory);
        updateMenuBars();
    }

    /**
     * Removes a menu from menubar.
     * 
     * @param factory
     *            that will create the menu.
     */
    public static boolean removeMenu(MysterMenuFactory factory) {
        getFactory(); //assert menu bar stuff is loaded.

        boolean sucess = menuBar.removeElement(factory);
        updateMenuBars();
        return sucess;
    }

    /**
     * Adds a new menu item to the end of the plugins menu. Creates the plugins
     * menu if needed.
     * 
     * @param menuItemfactory
     *            that will create the menu item.
     */
    public static void addMenuItem(MysterMenuItemFactory menuItemfactory) {
        getFactory(); //assert menu bar stuff is loaded.

        if (plugins.size() == 0) {
            menuBar.addElement(pluginMenuFactory);
        }
        plugins.addElement(menuItemfactory);
        updateMenuBars();
    }

    /**
     * Removes the menu item from the plugins menu. Removes the plugins menu if
     * it contains no more items.
     * 
     * @param menuItemfactory
     *            that will create the menu item.
     */
    public static boolean removeMenuItem(MysterMenuItemFactory menuItemfactory) {
        getFactory(); //assert menu bar stuff is loaded.

        boolean success = false;

        success = plugins.removeElement(menuItemfactory);

        if (plugins.size() == 0) {
            menuBar.removeElement(pluginMenuFactory);
        }

        updateMenuBars();
        return success;
    }
}
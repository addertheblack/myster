/* 

 Title:			Myster Open Source
 Author:			Andrew Trumper
 Description:	Generic Myster Code
 
 This code is under GPL

 Copyright Andrew Trumper 2000-2001
 */

package com.myster.menubar;

import java.awt.MenuBar;
import java.util.Vector;

import com.general.events.EventDispatcher;
import com.general.events.SyncEventDispatcher;
import com.general.util.Util;
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

/**
 * Is the global Myster MenuBar. NOTE: On the macintosh it's important to have a
 * global menu bar. The menu bar object has a groovy constructor that works with
 * the MysterMenuObject in order to make the taks of adding new menu items easy.
 * 
 * Warning this code is not even close to being thread safe.
 */

public class MysterMenuBar extends MenuBar {
    private static final NullAction NULL = new NullAction();

    /** Static sub-system is below */
    static EventDispatcher dispatcher = new SyncEventDispatcher();

    private static MysterMenuBarFactory impl;

    private static Vector file, edit, special, menuBar, plugins;

    private static MysterMenuFactory pluginMenuFactory;

    private static synchronized MysterMenuBarFactory getFactory() {
        if (impl == null) {
            file = new Vector();
            edit = new Vector();
            special = new Vector();

            //File menu items
            
            file.addElement(new MysterMenuItemFactory("New Search", new NewSearchWindowAction(),
                    java.awt.event.KeyEvent.VK_N));
            file.addElement(new MysterMenuItemFactory("New Peer-to-Peer Connection",
                    new NewClientWindowAction(), java.awt.event.KeyEvent.VK_N, true));
            file.addElement(new MysterMenuItemFactory("New Instant Message",
                    new java.awt.event.ActionListener() {
                        public void actionPerformed(java.awt.event.ActionEvent e) {
                            com.myster.message.MessageWindow window = new com.myster.message.MessageWindow();
                            window.setVisible(true);
                        }
                    }));
            file.addElement(new MysterMenuItemFactory("Close Window", new CloseWindowAction(),
                    java.awt.event.KeyEvent.VK_W));
            file.addElement(new MysterMenuItemFactory("-", NULL));
            
            file.addElement(new MysterMenuItemFactory("Quit", new QuitMenuAction(),
                    java.awt.event.KeyEvent.VK_Q));


            //Edit menu items
            edit.addElement(new MysterMenuItemFactory("Undo", NULL));
            edit.addElement(new MysterMenuItemFactory("-", NULL));
            edit.addElement(new MysterMenuItemFactory("Cut", NULL));
            edit.addElement(new MysterMenuItemFactory("Copy (use command-c)", NULL));
            edit.addElement(new MysterMenuItemFactory("Paste (use command-v)", NULL));
            edit.addElement(new MysterMenuItemFactory("Clear", NULL));
            edit.addElement(new MysterMenuItemFactory("-", NULL));
            edit.addElement(new MysterMenuItemFactory("Select All", NULL));
            edit.addElement(new MysterMenuItemFactory("-", NULL));
            edit.addElement(new MysterMenuItemFactory("Preferences", new PreferencesAction(),
                    java.awt.event.KeyEvent.VK_SEMICOLON));

            //Disable all Edit menu commands
            for (int i = 0; i < edit.size() - 1; i++) {
                ((MysterMenuItemFactory) (edit.elementAt(i))).setEnabled(false);
            }

            //Myster menu items
            special.addElement(new MysterMenuItemFactory("Add IP", new AddIPMenuAction()));
            special.addElement(new MysterMenuItemFactory("Show Server Stats",
                    new StatsWindowAction(), java.awt.event.KeyEvent.VK_S, true));
            special.addElement(new MysterMenuItemFactory("Show Tracker", new TrackerWindowAction(),
                    java.awt.event.KeyEvent.VK_T));
            special.addElement(com.myster.hash.ui.HashManagerGUI.getMenuItem());

            //Myster plugins Menu
            plugins = new Vector();
            pluginMenuFactory = new MysterMenuFactory("Plugins", plugins);

            menuBar = new Vector();
            menuBar.addElement(new MysterMenuFactory("File", file));
            menuBar.addElement(new MysterMenuFactory("Edit", edit));
            menuBar.addElement(new MysterMenuFactory("Special", special));
            //plugins menu is not added here.

            impl = new MysterMenuBarFactory(menuBar);
        }

        return impl;
    }

    public static void addMenuListener(MenuBarListener listener) { //Not
        // sycnhronized
        dispatcher.addListener(listener);
        listener.fireEvent(new MenuBarEvent(MenuBarEvent.BAR_CHANGED, getFactory()));
    }

    public static void removeMenuListener(MenuBarListener listener) { //Not
        // synchronized
        dispatcher.removeListener(listener);
        //System.gc();
    }

    public static boolean removeBuiltInMenu(String menuName) {
        getFactory(); //assert menu bar stuff is loaded.
        for (int i = 0; i < menuBar.size(); i++) {
            if (((MysterMenuFactory) (menuBar.elementAt(i))).getName().equalsIgnoreCase(menuName)) {
                menuBar.removeElementAt(i);
                dispatcher.fireEvent(new MenuBarEvent(MenuBarEvent.BAR_CHANGED, getFactory()));
                return true;
            }
        }
        return false;
    }

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

    public static void updateMenuBars() {
        //Util.invoke(new Runnable() {
            //public void run() {
                dispatcher.fireEvent(new MenuBarEvent(MenuBarEvent.BAR_CHANGED, getFactory()));
           // }
        //});
    }

    public static void addMenu(MysterMenuFactory factory) {
        getFactory(); //assert menu bar stuff is loaded.

        menuBar.addElement(factory);
        updateMenuBars();
    }

    public static boolean removeMenu(MysterMenuFactory factory) {
        getFactory(); //assert menu bar stuff is loaded.

        boolean sucess = menuBar.removeElement(factory);
        updateMenuBars();
        return sucess;
    }

    public static void addMenuItem(MysterMenuItemFactory menuItemfactory) {
        getFactory(); //assert menu bar stuff is loaded.

        if (plugins.size() == 0) {
            menuBar.addElement(pluginMenuFactory);
        }
        plugins.addElement(menuItemfactory);
        updateMenuBars();
    }

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
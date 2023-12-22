/* 

 Title:			Myster Open Source
 Author:		Andrew Trumper
 Description:	Generic Myster Code
 
 This code is under GPL

 Copyright Andrew Trumper 2000-2004
 */

package com.myster.ui.menubar;

import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JFrame;
import javax.swing.JMenuBar;
import javax.swing.KeyStroke;
import javax.swing.text.JTextComponent;

import com.general.events.AsyncEventThreadDispatcher;
import com.general.events.EventDispatcher;
import com.myster.tracker.IpListManager;
import com.myster.ui.MysterFrameContext;
import com.myster.ui.PreferencesGui;
import com.myster.ui.menubar.event.AddIPMenuAction;
import com.myster.ui.menubar.event.CloseWindowAction;
import com.myster.ui.menubar.event.MenuBarEvent;
import com.myster.ui.menubar.event.MenuBarListener;
import com.myster.ui.menubar.event.NewClientWindowAction;
import com.myster.ui.menubar.event.NewSearchWindowAction;
import com.myster.ui.menubar.event.NullAction;
import com.myster.ui.menubar.event.PreferencesAction;
import com.myster.ui.menubar.event.QuitMenuAction;
import com.myster.ui.menubar.event.StatsWindowAction;
import com.myster.ui.menubar.event.TrackerWindowAction;

/**
 * Is the global Myster MenuBar. NOTE: On the macintosh it's important to have a
 * global menu bar. The menu bar object has a groovy constructor that works with
 * the MysterMenuObject in order to make the taks of adding new menu items easy.
 * 
 * Warning this code is not even close to being thread safe. Only call it on the
 * event thread.
 */
public class MysterMenuBar {
    private static final NullAction NULL = new NullAction();

    /** Static sub-system is below */
    private EventDispatcher dispatcher = new AsyncEventThreadDispatcher();

    private final MysterMenuBarFactory mysterMenuBarFactory;
    private MysterMenuBarFactory mysterMenuBarFactoryImpl;

    private  List<MysterMenuItemFactory> file, edit, special,  plugins;
    private  List<MysterMenuFactory> menuBarFactories;

    private  MysterMenuFactory pluginMenuFactory;

    public MysterMenuBar() {
        mysterMenuBarFactory = newMysterMenubarFactory();
    }

    public void initMenuBar(IpListManager manager, PreferencesGui prefGui) {
        file = new ArrayList<>();
        edit = new ArrayList<>();
        special = new ArrayList<>();

        MysterFrameContext context = new MysterFrameContext(this);

        // File menu items
        file.add(new MysterMenuItemFactory("New Search",
                                                  new NewSearchWindowAction(context),
                                                  java.awt.event.KeyEvent.VK_N));
        file.add(new MysterMenuItemFactory("New Peer-to-Peer Connection",
                                                  new NewClientWindowAction(context),
                                                  java.awt.event.KeyEvent.VK_N,
                                                  true));
        file.add(new MysterMenuItemFactory("New Instant Message",
                                                  (java.awt.event.ActionEvent e) -> {
                                                      com.myster.message.MessageWindow window =
                                                              new com.myster.message.MessageWindow(context);
                                                      window.setVisible(true);
                                                  }));
        file.add(new MysterMenuItemFactory("Close Window",
                                                  new CloseWindowAction(),
                                                  java.awt.event.KeyEvent.VK_W));
        file.add(new MysterMenuItemFactory("-", NULL));

        file.add(new MysterMenuItemFactory("Quit",
                                                  new QuitMenuAction(),
                                                  java.awt.event.KeyEvent.VK_Q));

        // Edit menu items
//        edit.add(new MysterMenuItemFactory(createCutCopyPasteMenu("Undo", KeyEvent.VK_Z, (t) -> t.keySt))));
        edit.add(new MysterMenuItemFactory("Undo", NULL));
        edit.add(new MysterMenuItemFactory());
        edit.add(new MysterMenuItemFactory(createCutCopyPasteMenu("Cut", KeyEvent.VK_X, (t) -> t.cut())));
        edit.add(new MysterMenuItemFactory(createCutCopyPasteMenu("Copy", KeyEvent.VK_C, (t) -> t.copy())));
        edit.add(new MysterMenuItemFactory(createCutCopyPasteMenu("Paste", KeyEvent.VK_V, (t) -> t.paste())));
        
        edit.add(new MysterMenuItemFactory());
        edit.add(new MysterMenuItemFactory(createCutCopyPasteMenu("Select All",
                                                                  KeyEvent.VK_A,
                                                                  (t) -> t.selectAll())));
        edit.add(new MysterMenuItemFactory("-", NULL));
        edit.add(new MysterMenuItemFactory("Preferences",
                                           new PreferencesAction(prefGui),
                                           java.awt.event.KeyEvent.VK_SEMICOLON));


        // Myster menu items
        special.add(new MysterMenuItemFactory("Add IP", new AddIPMenuAction(manager)));
        special.add(new MysterMenuItemFactory("Show Server Stats",
                                                     new StatsWindowAction(),
                                                     java.awt.event.KeyEvent.VK_S,
                                                     true));
        special.add(new MysterMenuItemFactory("Show Tracker",
                                                     new TrackerWindowAction(),
                                                     java.awt.event.KeyEvent.VK_T));
        special.add(com.myster.hash.ui.HashManagerGUI.getMenuItem());

        // Myster plugins Menu
        plugins = new ArrayList<MysterMenuItemFactory>();
        pluginMenuFactory = new MysterMenuFactory("Plugins", plugins);

        menuBarFactories = new ArrayList<MysterMenuFactory>();
        menuBarFactories.add(new MysterMenuFactory("File", file));
        menuBarFactories.add(new MysterMenuFactory("Edit", edit));
        menuBarFactories.add(new MysterMenuFactory("Special", special));
        // plugins menu is not added here.

        mysterMenuBarFactoryImpl = new DefaultMysterMenuBarFactory(menuBarFactories);

        updateMenuBars();
    }
    
    private static AbstractAction createCutCopyPasteMenu(String name, int key, Consumer<JTextComponent> c) {
        final JTextComponent[] a = new JTextComponent[1];
        AbstractAction action = new AbstractAction(name) {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (a[0]!= null) {
                    c.accept(a[0]);
                }
            }
        };
        
        action.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(key, 0));
        
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener("permanentFocusOwner", new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                Component focusedComponent = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                boolean isEnabled = focusedComponent instanceof JTextComponent;
                action.setEnabled(isEnabled);
                if (isEnabled) {
                    a[0] = (JTextComponent) focusedComponent;
                }
            }
        });
        
        action.setEnabled(true);
        
        return action;
    }

    private MysterMenuBarFactory newMysterMenubarFactory() {
        final var menuBarFactory = new MysterMenuBarFactory() {
            @Override
            public JMenuBar makeMenuBar(JFrame frame) {
                return mysterMenuBarFactoryImpl == null ? new JMenuBar() : mysterMenuBarFactoryImpl.makeMenuBar(frame);
            }

            @Override
            public int getMenuCount() {
                return mysterMenuBarFactoryImpl == null ? 0 : mysterMenuBarFactoryImpl.getMenuCount();
            }
        };
        
        return menuBarFactory;
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
    public  void addMenuListener(MenuBarListener listener) { //Not
        dispatcher.addListener(listener);

        listener.fireEvent(new MenuBarEvent(MenuBarEvent.BAR_CHANGED, mysterMenuBarFactory));
    }

    /**
     * Removes a MenuBarListener.
     * 
     * This method is thread safe.
     * 
     * @param listener
     *            to remove.
     */
    public  void removeMenuListener(MenuBarListener listener) { //Not
        dispatcher.removeListener(listener);
    }

    /**
     * Removes a built in menu by name.
     * 
     * @param menuName
     *            of the built in menu to remove.
     * @return returns true if menu was found and removed, false otherwise.
     */
    public  boolean removeBuiltInMenu(String menuName) {
        for (int i = 0; i < menuBarFactories.size(); i++) {
            if (menuBarFactories.get(i).getName().equalsIgnoreCase(menuName)) {
                menuBarFactories.remove(i);
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
    public  boolean removeBuiltInMenuItem(String menuName, String menuItem) {
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

    private  boolean removeMenuItem(List<MysterMenuItemFactory> list, String menuItem) { //ugh...
        for (int i = 0; i < list.size(); i++) {
            MysterMenuItemFactory item = list.get(i);
            if (item.getName().equalsIgnoreCase(menuItem)) {
                list.remove(i);
                while ((list.size() > 0) && ((list.get(list.size() - 1)).getName().equals("-"))) {
                    list.remove(list.size() - 1);
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
    private  void updateMenuBars() {
        dispatcher.fireEvent(new MenuBarEvent(MenuBarEvent.BAR_CHANGED, mysterMenuBarFactory));
    }

    /**
     * Adds a menu to the end of the menubar.
     * 
     * @param factory
     *            that will create the menu.
     */
    public  void addMenu(MysterMenuFactory factory) {
        menuBarFactories.add(factory);
        updateMenuBars();
    }

    /**
     * Removes a menu from menubar.
     * 
     * @param factory
     *            that will create the menu.
     */
    public  boolean removeMenu(MysterMenuFactory factory) {
        boolean sucess = menuBarFactories.remove(factory);
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
    public  void addMenuItem(MysterMenuItemFactory menuItemfactory) {
        if (plugins.size() == 0) {
            menuBarFactories.add(pluginMenuFactory);
        }
        plugins.add(menuItemfactory);
        updateMenuBars();
    }

    /**
     * Removes the menu item from the plugins menu. Removes the plugins menu if
     * it contains no more items.
     * 
     * @param menuItemfactory
     *            that will create the menu item.
     */
    public  boolean removeMenuItem(MysterMenuItemFactory menuItemfactory) {
        boolean success = false;

        success = plugins.remove(menuItemfactory);

        if (plugins.size() == 0) {
            menuBarFactories.remove(pluginMenuFactory);
        }

        updateMenuBars();
        return success;
    }
}
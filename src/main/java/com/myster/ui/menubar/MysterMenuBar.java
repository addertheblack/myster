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
import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;
import javax.swing.text.JTextComponent;
import javax.swing.undo.UndoManager;

import com.general.events.NewGenericDispatcher;
import com.general.thread.Invoker;
import com.myster.application.MysterGlobals;
import com.myster.client.net.MysterProtocol;
import com.myster.tracker.Tracker;
import com.myster.type.TypeDescriptionList;
import com.myster.ui.MysterFrameContext;
import com.myster.ui.PreferencesGui;
import com.myster.ui.WindowManager;
import com.myster.ui.menubar.event.AddMysterServerMenuAction;
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
    private NewGenericDispatcher<MenuBarListener> dispatcher =
            new NewGenericDispatcher<>(MenuBarListener.class, Invoker.EDT);

    private final MysterMenuBarFactory mysterMenuBarFactory;
    private MysterMenuBarFactory mysterMenuBarFactoryImpl;

    private  List<MysterMenuItemFactory> file, edit, special,  plugins;
    private  List<MysterMenuFactory> menuBarFactories;

    private  MysterMenuFactory pluginMenuFactory;
    
    private CloseWindowAction closeWindowAction;

    public MysterMenuBar() {
        mysterMenuBarFactory = newMysterMenubarFactory();
    }

    // note that this is construction time dependencies
    public void initMenuBar(Tracker manager, PreferencesGui prefGui, WindowManager windowManager, MysterProtocol protocol, TypeDescriptionList tdList) {
        file = new ArrayList<>();
        edit = new ArrayList<>();
        special = new ArrayList<>();

        MysterFrameContext context = new MysterFrameContext(this, windowManager, tdList);

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
                                                              new com.myster.message.MessageWindow(context, protocol);
                                                      window.setVisible(true);
                                                  }));
        closeWindowAction = new CloseWindowAction("Close Window", windowManager);
        closeWindowAction.putValue(Action.ACCELERATOR_KEY,
                                   KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_W, 0));
        file.add(new MysterMenuItemFactory(closeWindowAction));
        file.add(new MysterMenuItemFactory("-", NULL));

		if (!MysterGlobals.ON_MAC) {
			file.add(new MysterMenuItemFactory("Exit", new QuitMenuAction(), java.awt.event.KeyEvent.VK_Q));
		}

        // Edit menu items
		DynamicUndoRedo undoRepo = new DynamicUndoRedo();
		
		edit.add(new MysterMenuItemFactory(undoRepo.getUndoAction()));
		edit.add(new MysterMenuItemFactory(undoRepo.getRedoAction()));
        edit.add(new MysterMenuItemFactory());
        edit.add(new MysterMenuItemFactory(createCutCopyPasteMenu("Cut", KeyEvent.VK_X, (t) -> t.cut())));
        edit.add(new MysterMenuItemFactory(createCutCopyPasteMenu("Copy", KeyEvent.VK_C, (t) -> t.copy())));
        edit.add(new MysterMenuItemFactory(createCutCopyPasteMenu("Paste", KeyEvent.VK_V, (t) -> t.paste())));
        
		edit.add(new MysterMenuItemFactory());
		edit.add(new MysterMenuItemFactory(createCutCopyPasteMenu("Select All", KeyEvent.VK_A, (t) -> t.selectAll())));
		
		if (!MysterGlobals.ON_MAC) {
			edit.add(new MysterMenuItemFactory("-", NULL));
			edit.add(new MysterMenuItemFactory("Preferences", new PreferencesAction(prefGui),
					java.awt.event.KeyEvent.VK_SEMICOLON));
		}


        // Myster menu items
        special.add(new MysterMenuItemFactory("Add IP", new AddMysterServerMenuAction(manager)));
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
        
        windowManager.addMenus(this);

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
                action.setEnabled(isEnabled && focusedComponent.isShowing());
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

        listener.stateChanged(new MenuBarEvent(mysterMenuBarFactory));
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
        dispatcher.fire().stateChanged(new MenuBarEvent(mysterMenuBarFactory));
    }

    /**
     * Adds a menu to the end of the menubar.
     * 
     * @param factory
     *            that will create the menu.
     */
    public void addMenu(MysterMenuFactory factory) {
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

class DynamicUndoRedo {
    private UndoManager undoManager;
    private JTextComponent currentTextComponent;
    private UndoableEditListener undoableEditListener;
    private Action undoAction, redoAction;

    public DynamicUndoRedo() {
        undoManager = new UndoManager();
        undoableEditListener = new UndoableEditListener() {
            public void undoableEditHappened(UndoableEditEvent e) {
                undoManager.addEdit(e.getEdit());
                updateUndoRedoActions();
            }
        };

        // Initialize actions
        initActions();

        // Monitor focus changes to text components
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener("permanentFocusOwner", new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (evt.getNewValue() instanceof JTextComponent) {
                    setCurrentTextComponent((JTextComponent) evt.getNewValue());
                } else {
                    setCurrentTextComponent(null);
                }
            }
        });
    }

    private void initActions() {
        undoAction = new AbstractAction("Undo") {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (undoManager.canUndo()) {
                    undoManager.undo();
                }
                updateUndoRedoActions();
            }
        };
        undoAction.setEnabled(false);
        undoAction.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_Z, 0));

        redoAction = new AbstractAction("Redo") {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (undoManager.canRedo()) {
                    undoManager.redo();
                }
                updateUndoRedoActions();
            }
        };
        redoAction.setEnabled(false);
        redoAction.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_Z, KeyEvent.SHIFT_DOWN_MASK));
    }

    private void setCurrentTextComponent(JTextComponent newComponent) {
        if (currentTextComponent != null) {
            // Remove listener from the old component
            currentTextComponent.getDocument().removeUndoableEditListener(undoableEditListener);
        }

        currentTextComponent = newComponent;
        undoManager.discardAllEdits(); // Clear undo/redo history when focus changes

        if (currentTextComponent != null) {
            // Add listener to the new component
            currentTextComponent.getDocument().addUndoableEditListener(undoableEditListener);
        }

        updateUndoRedoActions();
    }

    private void updateUndoRedoActions() {
        undoAction.setEnabled(undoManager.canUndo());
        redoAction.setEnabled(undoManager.canRedo());
    }

    public Action getUndoAction() {
        return undoAction;
    }

    public Action getRedoAction() {
        return redoAction;
    }
}
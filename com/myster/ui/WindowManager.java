package com.myster.ui;

import java.awt.Frame;
import java.awt.Menu;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import com.myster.menubar.MysterMenuBar;
import com.myster.menubar.MysterMenuFactory;
import com.myster.menubar.MysterMenuItemFactory;
import com.myster.menubar.event.NullAction;

public class WindowManager {
    static Vector windows = new Vector();

    static MysterFrame frontMost;

    static Vector menuItems;

    static Vector finalMenu;

    static Runnable doUpdateClass = new Runnable() {
        public void run() {
            updateMenu();
        }
    };

    protected static void addWindow(MysterFrame frame) {
        synchronized (windows) {
            if (!windows.contains(frame)) {
                windows.addElement(frame);
                windowMenuHash.put(frame, (new MysterMenuFactory("Windows", finalMenu))
                        .makeMenu(frame));
                //Timer t=new Timer(doUpdateClass, 1);//might cause deadlocks.
                updateMenu();
            }
        }

    }

    protected static void removeWindow(MysterFrame frame) {
        boolean yep = windows.removeElement(frame);
        windowMenuHash.remove(frame);
        if (yep) {
            //Timer t=new Timer(doUpdateClass, 1); //might cause deadlocks.
            updateMenu();
        }
    }

    public static void updateMenu() {
        synchronized (windows) {
            init();

            finalMenu = new Vector(windows.size() + menuItems.size());

            for (int i = 0; i < menuItems.size(); i++) {
                finalMenu.addElement(menuItems.elementAt(i));
            }

            finalMenu.addElement(new MysterMenuItemFactory()); //is a seperator

            for (int i = 0; i < windows.size(); i++) {
                MysterFrame frame = ((MysterFrame) (windows.elementAt(i)));
                finalMenu.addElement(new MysterMenuItemFactory(frame.getTitle(),
                        new OtherWindowHandler(frame)));
            }
            
            Enumeration enum = windowMenuHash.elements();
            while (enum.hasMoreElements()) {
                fixMenu((Menu)enum.nextElement());
            }
            
            System.out.println(""+windows.size());
        }
    }

    private static void fixMenu(Menu menu) {
        for (int i = menu.getItemCount(); i > 3; i--) {
            menu.remove(i - 1);
        }

        for (int i = 0; i < windows.size(); i++) {
            MysterFrame frame = ((MysterFrame) (windows.elementAt(i)));
            menu.add((new MysterMenuItemFactory(frame.getTitle(), new OtherWindowHandler(frame)))
                    .makeMenuItem(frame));
        }
    }

    private static Hashtable windowMenuHash = new Hashtable();

    private static Menu getCorrectWindowsMenu(Frame frame) {
        Menu menu = (Menu) windowMenuHash.get(frame);
        if (menu == null) {
       // return new Menu("jello");
            throw new IllegalStateException("This frame has no windows menu! "+frame.getTitle());
            //menu = (new MysterMenuFactory("Windows",
            // finalMenu)).makeMenu(frame);
            //windowMenuHash.put(frame, menu);
        }
        return menu;
    }

    protected static void setFrontWindow(MysterFrame frame) {
        frontMost = frame;
    }

    public static MysterFrame getFrontMostWindow() {
        return frontMost;
    }

    static boolean isInited;

    public static void init() {
        if (isInited)
            return; //complain

        isInited = true;

        menuItems = new Vector();

        menuItems.addElement(new MysterMenuItemFactory("Cycle Windows", new CycleWindowsHandler(),
                KeyEvent.VK_1));
        menuItems.addElement(new MysterMenuItemFactory("Stack Windows", new StackWindowsHandler()));
        menuItems.addElement(new MysterMenuItemFactory("-", new NullAction()));

        finalMenu = new Vector();

        MysterMenuBar.addMenu(new MysterMenuFactory("Windows", finalMenu) {
            public Menu makeMenu(Frame frame) {
                return getCorrectWindowsMenu(frame);
            }
        });
        updateMenu();
    }

    private static class CycleWindowsHandler implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            Vector windows = WindowManager.windows;

            synchronized (windows) {
                if (windows.size() <= 0)
                    return; //(could happen if none-tracked window is
                // frontmost)

                int index = windows.indexOf(getFrontMostWindow());

                if (index == -1) {
                    index = 0;
                }

                index++;

                if (index >= windows.size()) {
                    index = 0;
                }

                ((MysterFrame) (windows.elementAt(index))).toFront();
            }
        }
    }

    private static class StackWindowsHandler implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            Vector windows = WindowManager.windows;

            final int MOD = 7;

            synchronized (windows) {
                for (int i = 0; i < windows.size(); i++) {
                    ((MysterFrame) (windows.elementAt(i))).setLocation(new java.awt.Point(
                            ((i % MOD) * 20) + 10, (i % MOD) * 20 + ((i / MOD) * 20) + 10
                                    + ((MysterFrame) (windows.elementAt(i))).getInsets().top));
                }

                for (int i = 0; i < windows.size(); i++) {
                    ((MysterFrame) (windows.elementAt(i))).toFront();
                }
            }
        }
    }

    private static class OtherWindowHandler implements ActionListener {
        MysterFrame frame;

        public OtherWindowHandler(MysterFrame frame) {
            this.frame = frame;
        }

        public void actionPerformed(ActionEvent e) {
            frame.toFront();
        }
    }
}
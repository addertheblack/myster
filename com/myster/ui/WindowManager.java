package com.myster.ui;

import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import javax.swing.JFrame;
import javax.swing.JMenu;

import com.myster.application.MysterGlobals;
import com.myster.menubar.MysterMenuBar;
import com.myster.menubar.MysterMenuFactory;
import com.myster.menubar.MysterMenuItemFactory;
import com.myster.menubar.event.NullAction;

/**
 * This class is responsible for managing the number and order of all Myster
 * windows. Unfortunately it has become overgrown and is now also managing the
 * "Windows" menu. It also is way too coupled with MysterFrame.
 */
public class WindowManager {
    private static final Map<MysterFrame, JMenu> windowMenuHash = new HashMap<>();

    private static final List<MysterFrame> windows = new ArrayList<>();

    private static MysterFrame frontMost;

    private static Vector menuItems;

    private static Vector finalMenu;

    public static void init() {
        synchronized (windows) {
            if (isInited) {
                throw new IllegalStateException("Tried to init WindowManager twice");
            }
            

            isInited = true;

            menuItems = new Vector();

            menuItems.addElement(new MysterMenuItemFactory("Cycle Windows", new CycleWindowsHandler(),
                    KeyEvent.VK_1));
            menuItems.addElement(new MysterMenuItemFactory("Stack Windows", new StackWindowsHandler()));
            menuItems.addElement(new MysterMenuItemFactory("-", new NullAction()));

            finalMenu = new Vector();

            MysterMenuBar.addMenu(new MysterMenuFactory("Windows", finalMenu) {
                @Override
                public JMenu makeMenu(JFrame frame) {
                    return getCorrectWindowsMenu(frame);
                }
            });
            updateMenu();
        }
    }
    
    protected static void addWindow(MysterFrame frame) {
        synchronized (windows) {
            if (!windows.contains(frame)) {
                windows.add(frame);
                windowMenuHash.put(frame, (new MysterMenuFactory("Windows", finalMenu))
                        .makeMenu(frame));
                // Timer t=new Timer(doUpdateClass, 1);//might cause deadlocks.
                updateMenu();
            }
        }

    }

    static void removeWindow(MysterFrame frame) {
        boolean yep = windows.remove(frame);
        windowMenuHash.remove(frame);
        if (yep) {
            // Timer t=new Timer(doUpdateClass, 1); //might cause deadlocks.
            updateMenu();
            if (windows.size() == 0) {
                //TODO: Fix this hack.
                if (MysterGlobals.ON_LINUX) { //hack hack hack!
                    MysterGlobals.quit();
                }
            }
        }
    }

    public static void updateMenu() {
        synchronized (windows) {
            if (!isInited)
                return;

            finalMenu = new Vector(windows.size() + menuItems.size());

            for (int i = 0; i < menuItems.size(); i++) {
                finalMenu.addElement(menuItems.elementAt(i));
            }

            finalMenu.addElement(new MysterMenuItemFactory()); // is a
            // seperator

            for (MysterFrame frame : windows) {
                finalMenu.addElement(new MysterMenuItemFactory(frame.getTitle(),
                                                               new OtherWindowHandler(frame)));
            }
            
            for (JMenu menu : windowMenuHash.values()) {
                fixMenu(menu);
            }
        }
    }

    private static void fixMenu(JMenu menu) {
        for (int i = menu.getItemCount(); i > 3; i--) {
            menu.remove(i - 1);
        }

        for (MysterFrame frame : windows) {
            new MysterMenuItemFactory(frame.getTitle(), new OtherWindowHandler(frame))
                    .makeMenuItem(frame, menu);
        }
    }

    private static JMenu getCorrectWindowsMenu(Frame frame) {
        JMenu menu = windowMenuHash.get(frame);
        if (menu == null) {
            return new JMenu("Windows");
            // throw new IllegalStateException("This frame has no windows menu!
            // " +
            // frame.getTitle());
            // menu = (new MysterMenuFactory("Windows",
            // finalMenu)).makeMenu(frame);
            // windowMenuHash.put(frame, menu);
        }
        return menu;
    }

    static void setFrontWindow(MysterFrame frame) {
        frontMost = frame;
    }

    public static MysterFrame getFrontMostWindow() {
        return frontMost;
    }

    static boolean isInited;

    private static class CycleWindowsHandler implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            List<MysterFrame> windows = WindowManager.windows;

            synchronized (windows) {
                if (windows.size() <= 0)
                    return; // (could happen if none-tracked window is
                // frontmost)

                int index = windows.indexOf(getFrontMostWindow());

                if (index == -1) {
                    index = 0;
                }

                MysterFrame frame;
                int counter = 0;
                do {
                    index++;
                    if (index >= windows.size()) {
                        index = 0;
                    }

                    frame = windows.get(index);
                    if (counter > windows.size()) {
                        throw new IllegalStateException(
                                "No MysterFrames with menu bars exist and yet we have been asked to do a window cycle!");
                    }
                    counter++;
                } while (!frame.isMenuBarEnabled());
                frame.toFrontAndUnminimize();
            }
        }
    }

    private static class StackWindowsHandler implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            List<MysterFrame> windows = WindowManager.windows;

            final int MOD = 7;

            synchronized (windows) {
                for (int i = 0; i < windows.size(); i++) {
                    (windows.get(i)).setLocation(new java.awt.Point(
                            ((i % MOD) * 20) + 10, (i % MOD) * 20 + ((i / MOD) * 20) + 10
                                    + (windows.get(i)).getInsets().top));
                }

                for (MysterFrame mysterFrame : windows) {
                    mysterFrame.toFront();
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